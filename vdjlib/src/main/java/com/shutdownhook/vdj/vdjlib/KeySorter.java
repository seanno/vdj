//
// KEYSORTER.JAVA
// 

// TODO: seems like these might like to be cached, but let's see
// how things play out before adding that complexity to the mix

package com.shutdownhook.vdj.vdjlib;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.RearrangementKey.Extractor;
import com.shutdownhook.vdj.vdjlib.model.FrameType;
import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class KeySorter implements Closeable
{
	// +--------+
	// | Config |
	// +--------+

	public static class Config
	{
		public int InitialChunkSize = 500000;
		public Boolean UseCache = true;
		public String WorkingPath = "/tmp";
	}

	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public KeySorter(ContextRepertoireStore crs, String repertoireName,
					 Extractor extractor, Config cfg) {
		
		this.crs = crs;
		this.repertoireName = repertoireName;
		this.extractor = extractor;
		this.cfg = cfg;
		this.workingPath = Paths.get(cfg.WorkingPath);
	}

	public void close() {
		if (reader != null) reader.close();
		cleanupFiles(files);
	}

	// +----------+
	// | readNext |
	// +----------+

	public KeyItem readNext() throws IOException {
		return(reader.readNext());
	}
	
	// +-----------+
	// | sortAsync |
	// | sort      |
	// +-----------+

	public CompletableFuture<Boolean> sortAsync() {
		
		CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();

		Exec.getPool().submit(() -> {

			try {
				
				if (!fetchFromCache()) {
					sort();
					reader = new KeyReader(files.get(0));
					maybeSaveToCache();
				}
				
				future.complete(true);
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "sort", true));
				future.complete(false);
			}
		});

		return(future);
	}
	
	private void sort() throws Exception {

		// 0. Allocate the focus of the action
		files = new ArrayList<File>();

		// 1. Chunk up the TSV (one thread for simplicity)
		initialSort();

		// 2. Iteratively combine chunks until only one left
		while (files.size() > 1) mergeFiles();
	}

	// +------------+
	// | mergeFiles |
	// +------------+

	// spawn threads to merge by twos. Note that while typically we can
	// count on close() to clean up temp files, once we pop them off of
	// the list, they're our problem to deal with.

	private void mergeFiles() throws Exception {

		List<File> mergeFiles = popAllFiles();

		try {

			// spin up threads to merge file pair
			int threadCount = (mergeFiles.size() / 2); // ok if odd or even
			List<CompletableFuture<Boolean>> futures = new ArrayList<CompletableFuture<Boolean>>();
				
			for (int i = 0; i < threadCount; ++i) {
				futures.add(mergeFilePairAsync(mergeFiles.get(i * 2),
											   mergeFiles.get((i * 2) + 1)));
			}

			// wait for them to be done ... ok to do serially like this
			for (int i = 0; i < threadCount; ++i) {
				if (!futures.get(i).get()) throw new Exception("Failed merging pair");
			}
			
			if ((threadCount * 2) != mergeFiles.size()) {
				// oops we had an odd # of files to merge; put the straggler back,
				// being sure to remove it from our list so we don't clean it up!
				File fileStraggler = mergeFiles.get(mergeFiles.size() - 1);
				log.finest(String.format("mergeFiles straggler: %s", fileStraggler.getName()));

				mergeFiles.remove(fileStraggler);
				addFile(fileStraggler);
			}
			
		}
		finally {
			cleanupFiles(mergeFiles);
		}
	}

	// +--------------------+
	// | mergeFilePairAsync |
	// | mergeFilePair      |
	// +--------------------+

	// merge and dedup two files, putting the result back onto this.files
	
	private CompletableFuture<Boolean> mergeFilePairAsync(File f1, File f2) {
		
		CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();

		Exec.getPool().submit(() -> {

			try {
				mergeFilePair(f1, f2);
				future.complete(true);
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "searchAsync", true));
				future.complete(false);
			}
		});

		return(future);
	}

	private void mergeFilePair(File f1, File f2) throws IOException {
		
		log.finest(String.format("mergeFilePair: f1=%s, f2=%s", f1.getName(), f2.getName()));

		KeyReader rdr1 = null;
		KeyReader rdr2 = null;
		KeyWriter wtr = null;

		try {

			rdr1 = new KeyReader(f1);
			rdr2 = new KeyReader(f2);

			File fileMerged = Files.createTempFile(workingPath, "vdj", ".txt").toFile();
			fileMerged.deleteOnExit();
			wtr = new KeyWriter(fileMerged);

			KeyItem item1 = rdr1.readNext();
			KeyItem item2 = rdr2.readNext();

			// write out in order, de-duping
			while (item1 != null && item2 != null) {
				
				int icmp = item1.compareTo(item2);
				
				if (icmp == 0) {
					item1.accumulateCount(item2.getCount());
					item2 = rdr2.readNext();
				}
				else if (icmp < 0) {
					wtr.write(item1);
					item1 = rdr1.readNext();
				}
				else {
					wtr.write(item2);
					item2 = rdr2.readNext();
				}
			}

			// spit out the balance
			
			while (item1 != null) { wtr.write(item1); item1 = rdr1.readNext(); }
			while (item2 != null) {	wtr.write(item2); item2 = rdr2.readNext(); }

			// and done!

			log.finest(String.format("mergeFilePair: adding merged file %s", fileMerged.getName()));
			addFile(fileMerged);
		}
		finally {
			if (rdr2 != null) Utility.safeClose(rdr2);
			if (rdr1 != null) Utility.safeClose(rdr1);
			if (wtr != null) Utility.safeClose(wtr);
		}
	}

	// +-------------+
	// | initialSort |
	// +-------------+

	// Fills this.files with a list of files. Each file is a chunk of the original
	// TSV content, keyed, deduped and counted.
	
	private void initialSort() throws Exception {

		InputStream stm = null;
		InputStreamReader rdr = null;
		TsvReader tsv = null;
		
		try {

			stm = crs.getRepertoireStream(repertoireName);
			rdr = new InputStreamReader(stm);
			tsv = new TsvReader(rdr, 0);
			
			KeyItem[] items = new KeyItem[cfg.InitialChunkSize];
			Rearrangement r;

			while (true) {

				// read the next chunk
				int count = 0;
				while ((r = tsv.readNext()) != null) {
					String key = extractor.extract(r);
					if (!Utility.nullOrEmpty(key)) {
						items[count++] = new KeyItem(key, r.Count);
						if (count == cfg.InitialChunkSize) break;
					}
				}

				if (count == 0) break;
				
				// sort it
				Arrays.sort(items, 0, count);

				// and write it out
				File file = writeToInitialSortFile(items, count);
				log.finest(String.format("initialSort: adding file %s with count %d",
										 file.getName(), count));
				addFile(file);
			}
		}
		finally {
			
			if (tsv != null) Utility.safeClose(tsv);
			if (rdr != null) Utility.safeClose(rdr);
			if (stm != null) Utility.safeClose(stm);
		}
	}

	private File writeToInitialSortFile(KeyItem[] items, int count) throws Exception {

		KeyWriter wtr = null;

		if (count == 0) throw new Exception("0 count file in key sorter");
			
		try {
			File file = Files.createTempFile(workingPath, "vdj", ".txt").toFile();
			file.deleteOnExit();
			wtr = new KeyWriter(file);

			KeyItem lastItem = items[0];
			for (int i = 1; i < count; ++i) {
				
				KeyItem curItem = items[i];
				
				if (lastItem.compareTo(curItem) == 0) {
					lastItem.accumulateCount(curItem.getCount());
				}
				else {
					wtr.write(lastItem);
					lastItem = curItem;
				}
			}

			wtr.write(lastItem);

			return(file);
		}
		finally {

			if (wtr != null) Utility.safeClose(wtr);
		}
	}

	// +------------------+
	// | fetchFromCache   |
	// | maybeSaveToCache |
	// +------------------+

	private boolean fetchFromCache() throws IOException {
		
		if (!cfg.UseCache) return(false);

		String cacheKey = getCacheKey();
		InputStream stm = crs.getSecondaryStream(repertoireName, cacheKey);
		if (stm == null) return(false);

		log.info(String.format("KeySorter cache hit for %s/%s", repertoireName, cacheKey));
		reader = new KeyReader(stm);
		return(true);
	}

	private void maybeSaveToCache() {

		if (!cfg.UseCache) return;

		try {
			String cacheKey = getCacheKey();
			crs.saveSecondaryFile(repertoireName, cacheKey, files.get(0));
			log.info(String.format("KeySorter cached result for %s/%s", repertoireName, cacheKey));
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "KeySorter save to cache (non-fatal)", true));
		}
	}

	private String getCacheKey() {
		return("keySorter-" + extractor.getClass().getName());
	}

	// +---------+
	// | KeyItem |
	// +---------+

	public static class KeyItem implements Comparable<KeyItem>
	{
		public KeyItem(String key, long initialCount) {
			this.key = key;
			this.count = initialCount;
		}

		public String getKey() { return(key); }
		public long getCount() { return(count); }

		public void accumulateCount(long addCount) { count += addCount; }
		
		public int compareTo(KeyItem item) {
			return(key.compareTo(item.getKey()));
		}

		public String toString() {
			return(String.format("%s\t%d", key, count));
		}

		public static KeyItem fromString(String input) {
			int ich = input.indexOf("\t");
			return(new KeyItem(input.substring(0, ich),
							   Long.parseLong(input.substring(ich+1))));
		}

		String key;
		long count;
	}

	// +---------------------------------+
	// | Synchronized File list handling |
	// +---------------------------------+

	private synchronized List<File> popAllFiles() {
		List<File> popped = files;
		files = new ArrayList<File>();
		return(popped);
	}
	
	private synchronized void addFile(File file) {
		this.files.add(file);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private void cleanupFiles(List<File> targetFiles) {
		if (targetFiles == null) return;
		for (File file : targetFiles) safeDelete(file);
	}

	private void safeDelete(File file) {
		try {
			file.delete();
		}
		catch (Exception e) {
			log.severe(Utility.exMsg(e, "safeDelete", false));
			/* eat it */
		}
	}

	// +-----------+
	// | KeyReader |
	// +-----------+

	public static class KeyReader implements Closeable
	{
		public KeyReader(InputStream stm) throws IOException {
			this.stm = stm;
			rdr = new InputStreamReader(stm);
			buf = new BufferedReader(rdr);
		}
		
		public KeyReader(File file) throws IOException {
			rdr = new FileReader(file);
			buf = new BufferedReader(rdr);
		}

		public KeyItem readNext() throws IOException {
			String line = buf.readLine();
			return(line == null ? null : KeyItem.fromString(line));
		}

		public void close() {
			if (buf != null) Utility.safeClose(buf);
			if (rdr != null) Utility.safeClose(rdr);
			if (stm != null) Utility.safeClose(stm);
		}

		private InputStream stm;
		private InputStreamReader rdr;
		private BufferedReader buf;
	}

	// +-----------+
	// | KeyWriter |
	// +-----------+

	public static class KeyWriter implements Closeable
	{
		public KeyWriter(File file) throws IOException {
			
			wtr = new FileWriter(file);
			buf = new BufferedWriter(wtr);
		}

		public void write(KeyItem item) throws IOException {
			buf.write(item.toString());
			buf.newLine();
		}

		public void close() {
			if (buf != null) Utility.safeClose(buf);
			if (wtr != null) Utility.safeClose(wtr);
		}

		public FileWriter wtr;
		public BufferedWriter buf;
	}

	// +---------+
	// | Members |
	// +---------+

	private ContextRepertoireStore crs;
	private String repertoireName;
	private Extractor extractor;
	private Config cfg;
	private Path workingPath;

	private List<File> files;

	private KeyReader reader;
	
	private final static Logger log = Logger.getLogger(KeySorter.class.getName());
}

