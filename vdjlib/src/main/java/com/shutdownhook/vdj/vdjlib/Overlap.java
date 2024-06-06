//
// OVERLAP.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.KeySorter.KeyExtractor;
import com.shutdownhook.vdj.vdjlib.KeySorter.KeyItem;
import com.shutdownhook.vdj.vdjlib.KeySorter.KeySorterParams;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class Overlap
{
	// +---------------+
	// | OverlapParams |
	// +---------------+

	public static enum OverlapByType
	{
		AminoAcid,
		CDR3
	}

	public static class OverlapParams
	{
		public Integer MaxOverlaps = 1000;
		public Integer MaxRepertoires = 6;
		public KeySorterParams KeySorter = new KeySorterParams();
	}

	// +---------------+
	// | OverlapResult |
	// +---------------+

	public static class OverlapResultItem implements Comparable<OverlapResultItem>
	{
		public String Key;
		public int PresentIn;
		public long[] Counts;
		public long MaxCount;

		public int compareTo(OverlapResultItem item) {
			
			if (item.MaxCount > MaxCount) return(1);
			if (item.MaxCount < MaxCount) return(-1);

			if (item.PresentIn > PresentIn) return(1);
			if (item.PresentIn < PresentIn) return(-1);
			
			if (item.Key.length() > Key.length()) return(1);
			if (item.Key.length() < Key.length()) return(-1);

			return(0);
		}
	}
	
	public static class OverlapResult
	{
		public List<Repertoire> Repertoires = new ArrayList<Repertoire>();
		public List<OverlapResultItem> Items = new ArrayList<OverlapResultItem>();
		public boolean Truncated = false;
	}

	// +--------------+
	// | overlapAsync |
	// | overlap      |
	// +--------------+

	public static CompletableFuture<OverlapResult> overlapAsync(ContextRepertoireStore crs,
																String[] repertoireNames,
																OverlapByType overlapBy,
																OverlapParams params) {
		
		CompletableFuture<OverlapResult> future = new CompletableFuture<OverlapResult>();

		Exec.getPool().submit(() -> {
				
			OverlapResult result = null;
			
			try {
				result = overlap(crs, repertoireNames, overlapBy, params);
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "overlapAsync", true));
			}
			
			future.complete(result);
		});

		return(future);
	}

	public static OverlapResult overlap(ContextRepertoireStore crs,
										String[] repertoireNames,
										OverlapByType overlapBy,
										OverlapParams params) throws Exception {

		if (repertoireNames.length > params.MaxRepertoires) {
			throw new Exception("Too many repertoires provided for Overlap");
		}

		List<KeySorter> sorters = null;

		try {

			OverlapResult result = new OverlapResult();

			// 1. Look up repertoires
		
			for (String name : repertoireNames) {
				Repertoire rep = crs.findRepertoire(name);
				if (rep == null) throw new Exception(String.format("Repertoire %s not found", name));
				result.Repertoires.add(rep);
			}

			// 2. KeySort each repertoire

			log.finest(String.format("KeySorting %d repertoires", result.Repertoires.size()));
			
			sorters = new ArrayList<KeySorter>();
			List<CompletableFuture<File>> futures = new ArrayList<CompletableFuture<File>>();
			KeyExtractor extractor = getKeyExtractor(overlapBy);
			
			for (Repertoire rep : result.Repertoires) {
				KeySorter sorter = new KeySorter(crs, rep.Name, extractor, params.KeySorter);
				sorters.add(sorter);
				futures.add(sorter.sortAsync());
			}

			for (int i = 0; i < futures.size(); ++i) {
				
				File file = futures.get(i).get();
				
				if (file == null) {
					throw new Exception("KeySorter failed for " +
										result.Repertoires.get(i).Name);
				}
			}
		
			// 3. Merge sorted repertoires into results
			findOverlaps(sorters, result, params);
			Collections.sort(result.Items);
			
			if (result.Items.size() > params.MaxOverlaps) {
				// sh*tter's full! Unfortunately we had to find ALL the overlaps
				// so that we could sort the goodest ones to the top, but at least
				// we can save some bandwidth and browser memory
				result.Items = result.Items.subList(0, params.MaxOverlaps);
				result.Truncated = true;
			}
				


			return(result);
		}
		finally {
			
			if (sorters != null) {
				for (KeySorter sorter : sorters) Utility.safeClose(sorter);
			}
		}
	}
	
	// +--------------+
	// | findOverlaps |
	// +--------------+

	public static class FindOverlapsState
	{
		public String Key;
		public int KeyMatches;
		public int[] KeyIndices;
		public int ActiveRepertoires;
	}
	
	private static void findOverlaps(List<KeySorter> sorters, OverlapResult result,
									 OverlapParams params) throws Exception {

		log.finest("finding overlaps");
		for (KeySorter sorter : sorters) sorter.initReader();

		FindOverlapsState state = new FindOverlapsState();
		state.KeyIndices = new int[sorters.size()];
			
		KeyItem items[] = new KeyItem[sorters.size()];
		for (int i = 0; i < items.length; ++i) items[i] = sorters.get(i).readNext();
		
		while (true) {

			assessOverlaps(items, state);

			// no more overlaps possible
			if (state.ActiveRepertoires < 2) break;

			if (state.KeyMatches > 1) {
				// yay an overlap!
				OverlapResultItem resultItem = new OverlapResultItem();
				result.Items.add(resultItem);
				
				resultItem.Key = state.Key;
				resultItem.PresentIn = state.KeyMatches;
				
				resultItem.Counts = new long[items.length];
				resultItem.MaxCount = -1;
				
				for (int i = 0; i < state.KeyMatches; ++i) {
					long thisCount = items[state.KeyIndices[i]].getCount();
					resultItem.Counts[state.KeyIndices[i]] = thisCount;
					if (thisCount > resultItem.MaxCount) resultItem.MaxCount = thisCount;
				}
			}

			// skip all at minimum
			for (int i = 0; i < state.KeyMatches; ++i) {
				items[state.KeyIndices[i]] = sorters.get(state.KeyIndices[i]).readNext();
			}
		}
	}

	private static void assessOverlaps(KeyItem[] items, FindOverlapsState state) {

		state.Key = null;
		state.KeyMatches = 0;
		state.ActiveRepertoires = 0;

		int i = 0;

		// skip over nulls
		while (i < items.length && items[i] == null) ++i;
		if (i == items.length) return;

		// start with this one
		state.Key = items[i].getKey();
		state.KeyMatches = 1;
		state.KeyIndices[0] = i;
		state.ActiveRepertoires = 1;
		++i;

		while (i < items.length) {

			if (items[i] != null) {

				state.ActiveRepertoires++;
			
				int cmp = state.Key.compareTo(items[i].getKey());

				if (cmp == 0) {
					// another!
					state.KeyIndices[state.KeyMatches++] = i;
				}
				else if (cmp > 0) {
					// reset to new min
					state.Key = items[i].getKey();
					state.KeyMatches = 1;
					state.KeyIndices[0] = i;
				}
				else {
					// skip bigger
				}
			}

			++i;
		}
	}

	// +-----------------+
	// | getKeyExtractor |
	// +-----------------+

	public static KeyExtractor getKeyExtractor(OverlapByType overlapBy) {

		switch (overlapBy) {
			case CDR3:
				return(new KeyExtractor() {
					public String extract(Rearrangement r) { return(r.getCDR3()); } });
				
			case AminoAcid:
				return(new KeyExtractor() {
					public String extract(Rearrangement r) { return(r.AminoAcid); } });
				
			default:
				return(null);
		}
	}

	// +---------+
	// | Members |
	// +---------+
	
	private final static Logger log = Logger.getLogger(Overlap.class.getName());
}

