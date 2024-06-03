//
// OVERLAPSORTER.JAVA
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

import com.shutdownhook.vdj.vdjlib.model.FrameType;
import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class OverlapSorter implements Closeable
{
	// +---------------+
	// | OverlapParams |
	// +---------------+

	public static enum OverlapByType
	{
		AminoAcid,
		CDR3
	}

	public static class OverlapSorterParams
	{
		public RepertoireStore Store;
		public String UserId;
		public String Context;
		public String Repertoire;
		public OverlapByType SortBy;
		
		public int InitialChunkSize = 500000;
		public String WorkingPath = "/tmp";
	}

	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public OverlapSorter(OverlapSorterParams params) {
		this.params = params;
		this.workingPath = Paths.get(params.WorkingPath);
		this.extractor = getKeyExtractor(params.SortBy);
	}

	public void close() {
		cleanupFiles(files);
	}
	
	// +-----------+
	// | sortAsync |
	// | sort      |
	// +-----------+

	public CompletableFuture<File> sortAsync() {
		
		CompletableFuture<File> future = new CompletableFuture<File>();

		Exec.getPool().submit(() -> {

			try {
				sort();
				future.complete(files.get(0));
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "sort", true));
				future.complete(null);
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

		FileReader rdr1 = null;
		FileReader rdr2 = null;
		BufferedReader buf1 = null;
		BufferedReader buf2 = null;
		FileWriter wtr = null;
		BufferedWriter bufWrite = null;

		try {

			rdr1 = new FileReader(f1);
			rdr2 = new FileReader(f2);
			buf1 = new BufferedReader(rdr1);
			buf2 = new BufferedReader(rdr2);

			File fileMerged = Files.createTempFile(workingPath, "vdj", ".txt").toFile();
			fileMerged.deleteOnExit();

			wtr = new FileWriter(fileMerged);
			bufWrite = new BufferedWriter(wtr);

			OverlapItem item1 = nextItem(buf1);
			OverlapItem item2 = nextItem(buf2);

			// write out in order, de-duping
			while (item1 != null && item2 != null) {
				
				int icmp = item1.compareTo(item2);
				
				if (icmp == 0) {
					item1.accumulateCount(item2.getCount());
					item2 = nextItem(buf2);
				}
				else if (icmp < 0) {
					writeTo(bufWrite, item1);
					item1 = nextItem(buf1);
				}
				else {
					writeTo(bufWrite, item2);
					item2 = nextItem(buf2);
				}
			}

			// spit out the balance
			
			while (item1 != null) { writeTo(bufWrite, item1); item1 = nextItem(buf1); }
			while (item2 != null) {	writeTo(bufWrite, item2); item2 = nextItem(buf2); }

			// and done!

			log.finest(String.format("mergeFilePair: adding merged file %s", fileMerged.getName()));
			addFile(fileMerged);
		}
		finally {
			if (buf2 != null) Utility.safeClose(buf2);
			if (buf1 != null) Utility.safeClose(buf1);
			if (rdr2 != null) Utility.safeClose(rdr2);
			if (rdr1 != null) Utility.safeClose(rdr1);
			if (bufWrite != null) Utility.safeClose(bufWrite);
			if (wtr != null) Utility.safeClose(wtr);
		}
	}

	private void writeTo(BufferedWriter buf, OverlapItem item) throws IOException {
		buf.write(item.toString());
		buf.newLine();
	}

	private OverlapItem nextItem(BufferedReader buf) throws IOException {
		String line = buf.readLine();
		return(line == null ? null : OverlapItem.fromString(line));
	}
	
	// +-------------+
	// | initialSort |
	// +-------------+

	// Fills this.files with a list of files. Each file is a chunk of the original
	// TSV content, deduped and counted.
	
	private void initialSort() throws Exception {

		InputStream stm = null;
		InputStreamReader rdr = null;
		TsvReader tsv = null;
		
		try {
			stm = params.Store.getRepertoireStream(params.UserId, params.Context, params.Repertoire);
			rdr = new InputStreamReader(stm);
			tsv = new TsvReader(rdr, 0);

			OverlapItem[] overlaps = new OverlapItem[params.InitialChunkSize];
			Rearrangement r;

			while (true) {

				// read the next chunk
				int count = 0;
				while ((r = tsv.readNext()) != null) {
					String key = extractor.extract(r);
					if (!Utility.nullOrEmpty(key)) {
						overlaps[count++] = new OverlapItem(key, r.Count);
						if (count == params.InitialChunkSize) break;
					}
				}

				if (count == 0) break;
				
				// sort it
				Arrays.sort(overlaps, 0, count);

				// and write it out
				File file = writeToInitialSortFile(overlaps, count);
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

	private File writeToInitialSortFile(OverlapItem[] overlaps, int count) throws Exception {

		FileWriter wtr = null;
		BufferedWriter buf = null;

		if (count == 0) throw new Exception("0 count file in overlap");
			
		try {
			File file = Files.createTempFile(workingPath, "vdj", ".txt").toFile();
			file.deleteOnExit();
			
			wtr = new FileWriter(file);
			buf = new BufferedWriter(wtr);

			OverlapItem lastItem = overlaps[0];
			for (int i = 1; i < count; ++i) {
				
				OverlapItem curItem = overlaps[i];
				
				if (lastItem.compareTo(curItem) == 0) {
					lastItem.accumulateCount(curItem.getCount());
				}
				else {
					buf.write(lastItem.toString());
					buf.newLine();
					lastItem = curItem;
				}
			}

			buf.write(lastItem.toString());
			buf.newLine();

			return(file);
		}
		finally {

			if (buf != null) Utility.safeClose(buf);
			if (wtr != null) Utility.safeClose(wtr);
		}
	}

	// +-------------+
	// | OverlapItem |
	// +-------------+

	public static class OverlapItem implements Comparable<OverlapItem>
	{
		public OverlapItem(String key, long initialCount) {
			this.key = key;
			this.count = initialCount;
		}

		public String getKey() { return(key); }
		public long getCount() { return(count); }

		public void accumulateCount(long addCount) { count += addCount; }
		
		public int compareTo(OverlapItem item) {
			return(key.compareTo(item.getKey()));
		}

		public String toString() {
			return(String.format("%s\t%d", key, count));
		}

		public static OverlapItem fromString(String input) {
			int ich = input.indexOf("\t");
			return(new OverlapItem(input.substring(0, ich),
								   Long.parseLong(input.substring(ich+1))));
		}

		String key;
		long count;
	}

	// +---------------------+
	// | OverlapKeyExtractor |
	// +---------------------+

	public interface OverlapKeyExtractor {
		public String extract(Rearrangement r);
	}

	public OverlapKeyExtractor getKeyExtractor(OverlapByType overlapBy) {

		switch (overlapBy) {
			case CDR3:
				return(new OverlapKeyExtractor() {
					public String extract(Rearrangement r) { return(r.getCDR3()); } });
				
			case AminoAcid:
				return(new OverlapKeyExtractor() {
					public String extract(Rearrangement r) { return(r.AminoAcid); } });
				
			default:
				return(null);
		}
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

	// +---------+
	// | Members |
	// +---------+

	private OverlapSorterParams params;
	private Path workingPath;
	private OverlapKeyExtractor extractor;

	private List<File> files;
	
	private final static Logger log = Logger.getLogger(OverlapSorter.class.getName());
}

