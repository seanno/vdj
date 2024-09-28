//
// OVERLAP.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.RearrangementKey.Extractor;
import com.shutdownhook.vdj.vdjlib.KeySorter;
import com.shutdownhook.vdj.vdjlib.KeySorter.KeyItem;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class Overlap
{
	// +--------+
	// | Config |
	// +--------+

	public static class Config
	{
		public Integer MaxRepertoires = 6;
		public Integer MaxStandardOverlaps = 1000;
		public Integer MaxCombinedKeyLength = 1024;
		public KeySorter.Config KeySorter = new KeySorter.Config();
	}

	public Overlap(Config cfg) {
		this.cfg = cfg;
	}

	// +---------------+
	// | OverlapParams |
	// +---------------+

	public static enum OverlapMode
	{
		/* return only keys present in > 1 rep, sorted by overlappiness */
		Standard,

		/* return all keys and counts, one entry per "presence profile" (Counts array) */
		/* no useful sort to results */
		Combined  
		
	}
	
	public static class Params
	{
		public ContextRepertoireStore CRS;
		public String[] RepertoireNames;
		public Extractor Extractor;
		public OverlapMode Mode = OverlapMode.Standard;
	}

	// +---------------+
	// | OverlapResult |
	// +---------------+

	public static class OverlapResultItem
	{
		public String Key;
		public int KeyCount;
		public int PresentIn;
		public long[] Counts;
		public long MaxCount;

		public void appendKey(String newKey, int cchMax) {

			++KeyCount;
			
			// already full
			if (Key.endsWith("...")) return;
			
			if ((Key.length() + newKey.length() + 2) > (cchMax - 3)) {
				// not enough space to add newKey, so add ellipsis and bail
				Key = Key + "...";
			}
			else {
				// add it
				Key = Key + ", " + newKey;
			}
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

	public CompletableFuture<OverlapResult> overlapAsync(Params params) {
		
		return(Exec.runAsync("overlap", new Exec.AsyncOperation() {
			public OverlapResult execute() throws Exception {
				return(overlap(params));
			}
		}));
	}

	public OverlapResult overlap(Params params) throws Exception {

		int repCount = params.RepertoireNames.length;
		
		if (repCount > cfg.MaxRepertoires) {
			throw new Exception("Too many repertoires provided for Overlap");
		}

		List<KeySorter> sorters = null;

		try {

			OverlapResult result = new OverlapResult();

			// 1. Look up repertoires
		
			for (String name : params.RepertoireNames) {
				Repertoire rep = params.CRS.findRepertoire(name);
				if (rep == null) throw new Exception(String.format("Repertoire %s not found", name));
				result.Repertoires.add(rep);
			}

			// 2. KeySort each repertoire

			log.finest(String.format("KeySorting %d repertoires", result.Repertoires.size()));
			
			sorters = new ArrayList<KeySorter>();
			List<CompletableFuture<Boolean>> futures = new ArrayList<CompletableFuture<Boolean>>();
			
			for (Repertoire rep : result.Repertoires) {
				KeySorter sorter = new KeySorter(params.CRS, rep.Name, params.Extractor, cfg.KeySorter);
				sorters.add(sorter);
				futures.add(sorter.sortAsync());
			}

			for (int i = 0; i < futures.size(); ++i) {
				if (!futures.get(i).get()) {
					throw new Exception("KeySorter failed for " +
										result.Repertoires.get(i).Name);
				}
			}
		
			// 3. Merge sorted repertoires into results
			findOverlaps(sorters, result, params.Mode);

			// 4. Generate final result
			if (params.Mode.equals(OverlapMode.Standard)) {

				result.Items.sort(new StandardResultItemComparator());
			
				if (result.Items.size() > cfg.MaxStandardOverlaps) {
					// sh*tter's full! Unfortunately we had to find ALL the overlaps
					// so that we could sort the goodest ones to the top, but at least
					// we can save some bandwidth and browser memory
					result.Items = result.Items.subList(0, cfg.MaxStandardOverlaps);
					result.Truncated = true;
				}
			}
			else if (result.Items.size() > 0) { // OverlapMode.Combined

				CombinedResultItemComparator comparator =
					new CombinedResultItemComparator();

				// sort so that equivalent count profiles are adjacent...
				result.Items.sort(comparator);

				// ...and combine where appropriate (into a new list to avoid bad shift perf)
				List<OverlapResultItem> newItems = new LinkedList<OverlapResultItem>();
				OverlapResultItem currentItem = result.Items.get(0);
				newItems.add(currentItem);
				
				for (int i = 1; i < result.Items.size(); ++i) {
					OverlapResultItem candidateItem = result.Items.get(i);
					
					if (comparator.compare(currentItem, candidateItem) == 0) {
						currentItem.appendKey(candidateItem.Key, cfg.MaxCombinedKeyLength);
					}
					else {
						newItems.add(candidateItem);
						currentItem = candidateItem;
					}
				}

				result.Items = newItems;
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
	
	private void findOverlaps(List<KeySorter> sorters, OverlapResult result,
							  OverlapMode mode) throws Exception {

		log.finest("finding overlaps");

		FindOverlapsState state = new FindOverlapsState();
		state.KeyIndices = new int[sorters.size()];
			
		KeyItem items[] = new KeyItem[sorters.size()];
		for (int i = 0; i < items.length; ++i) items[i] = sorters.get(i).readNext();

		int repCountToContinue = (mode.equals(OverlapMode.Standard) ? 2 : 1);
		
		Map<String,OverlapResultItem> singletons = (mode.equals(OverlapMode.Standard)
													? null : new HashMap<String,OverlapResultItem>());
		
		while (true) {

			assessOverlaps(items, state);

			// no more overlaps possible ?
			if (state.ActiveRepertoires < repCountToContinue) break;

			if (state.KeyMatches > 1) {
				// yay an overlap!
				result.Items.add(makeResultItem(state, items));
			}
			else if (singletons != null && state.KeyMatches == 1) {
				// not an overlap but tuck it away (compactly)
				String singletonKey = String.format("%d-%d", state.KeyIndices[0],
													items[state.KeyIndices[0]].getCount());

				OverlapResultItem resultItem = singletons.get(singletonKey);
				if (resultItem == null) {
					singletons.put(singletonKey, makeResultItem(state, items));
				}
				else {
					resultItem.appendKey(state.Key, cfg.MaxCombinedKeyLength);
				}
			}

			// skip all at minimum
			for (int i = 0; i < state.KeyMatches; ++i) {
				items[state.KeyIndices[i]] = sorters.get(state.KeyIndices[i]).readNext();
			}
		}

		// add singletons to the end of the result list
		if (singletons != null) {
			for (String singletonKey : singletons.keySet()) {
				result.Items.add(singletons.get(singletonKey));
			}
		}
	}

	private static OverlapResultItem makeResultItem(FindOverlapsState state, KeyItem[] items) {

		OverlapResultItem resultItem = new OverlapResultItem();
				
		resultItem.Key = state.Key;
		resultItem.KeyCount = 1;
		resultItem.PresentIn = state.KeyMatches;
				
		resultItem.Counts = new long[items.length];
		resultItem.MaxCount = -1;
				
		for (int i = 0; i < state.KeyMatches; ++i) {
			long thisCount = items[state.KeyIndices[i]].getCount();
			resultItem.Counts[state.KeyIndices[i]] = thisCount;
			if (thisCount > resultItem.MaxCount) resultItem.MaxCount = thisCount;
		}

		return(resultItem);
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

	// +-------------------------------+
	// | OverlapResultItem Comparators |
	// +-------------------------------+

	// sort for display; most "overlappy" at the top
	public static class StandardResultItemComparator implements Comparator<OverlapResultItem>
	{
		public int compare(OverlapResultItem o1, OverlapResultItem o2) {

			if (o1.MaxCount < o2.MaxCount) return(1);
			if (o1.MaxCount > o2.MaxCount) return(-1);

			if (o1.PresentIn < o2.PresentIn) return(1);
			if (o1.PresentIn > o2.PresentIn) return(-1);
			
			if (o1.Key.length() < o2.Key.length()) return(1);
			if (o1.Key.length() > o2.Key.length()) return(-1);

			return(0);
		}
	}

	public static class CombinedResultItemComparator implements Comparator<OverlapResultItem>
	{
		public int compare(OverlapResultItem o1, OverlapResultItem o2) {
			for (int i = 0; i < o1.Counts.length; ++i) {
				if (o1.Counts[i] < o2.Counts[i]) return(-1);
				if (o1.Counts[i] > o2.Counts[i]) return(1);
			}

			return(0);
		}
	}


	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(Overlap.class.getName());
}

