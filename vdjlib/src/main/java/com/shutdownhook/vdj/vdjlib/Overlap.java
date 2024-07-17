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
		public Integer MaxOverlaps = 1000;
		public Integer MaxRepertoires = 6;
		public KeySorter.Config KeySorter = new KeySorter.Config();
	}

	public Overlap(Config cfg) {
		this.cfg = cfg;
	}

	// +---------------+
	// | OverlapParams |
	// +---------------+

	public static class Params
	{
		public ContextRepertoireStore CRS;
		public String[] RepertoireNames;
		public Extractor Extractor;
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

	public CompletableFuture<OverlapResult> overlapAsync(Params params) {
		
		CompletableFuture<OverlapResult> future = new CompletableFuture<OverlapResult>();

		Exec.getPool().submit(() -> {
				
			OverlapResult result = null;
			
			try {
				result = overlap(params);
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "overlapAsync", true));
			}
			
			future.complete(result);
		});

		return(future);
	}

	public OverlapResult overlap(Params params) throws Exception {

		if (params.RepertoireNames.length > cfg.MaxRepertoires) {
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
			findOverlaps(sorters, result);
			Collections.sort(result.Items);
			
			if (result.Items.size() > cfg.MaxOverlaps) {
				// sh*tter's full! Unfortunately we had to find ALL the overlaps
				// so that we could sort the goodest ones to the top, but at least
				// we can save some bandwidth and browser memory
				result.Items = result.Items.subList(0, cfg.MaxOverlaps);
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
	
	private void findOverlaps(List<KeySorter> sorters, OverlapResult result) throws Exception {

		log.finest("finding overlaps");

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

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(Overlap.class.getName());
}

