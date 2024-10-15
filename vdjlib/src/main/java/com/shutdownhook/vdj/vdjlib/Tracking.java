//
// TRACKING.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class Tracking
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public Integer MinMatchLength = 25;
		public Integer MaxTargets = 50;
		public Integer DxOptionsCount = 20;
		public Double DxOptionsMinFractionOfLocus = .05;
	}
	
	public Tracking(Config cfg) {
		this.cfg = cfg;
	}

	// +--------+
	// | Params |
	// +--------+
	
	public static class Params
	{
		public ContextRepertoireStore CRS;
		public String[] Repertoires;
		public Rearrangement[] Targets;
	}
	
	// +---------+
	// | Results |
	// +---------+

	public static class TargetValues
	{
		public Rearrangement Target;
		public double[] Values; // in order of Repertoires
	}
	
	public static class Results
	{
		public Repertoire[] Repertoires;
		public TargetValues[] TargetValues; // in order of params.targets
	}

	// +--------------+
	// | track(Async) |
	// +--------------+

	public CompletableFuture<Results> trackAsync(Params params) {
		return(Exec.runAsync("track", new Exec.AsyncOperation() {
			public Results execute() throws Exception {
				return(track(params));
			}
		}));
	}

	public Results track(Params params) throws Exception {

		Results results = new Results();
		results.Repertoires = new Repertoire[params.Repertoires.length];
		
		// 1. Get and sort repertoires
		
		for (int i = 0; i < params.Repertoires.length; ++i) {
			Repertoire rep = params.CRS.findRepertoire(params.Repertoires[i]);
			if (rep == null) {
				throw new Exception(String.format("rep %s not found", params.Repertoires[i]));
			}
			results.Repertoires[i] = rep;
		}

		Arrays.sort(results.Repertoires, new RepertoireComparator());

		// 2. match targets in each repertoire in parallel
		
		List<CompletableFuture<long[]>> futures = new ArrayList<CompletableFuture<long[]>>();
		
		for (int i = 0; i < results.Repertoires.length; ++i) {
			futures.add(matchTargetsAsync(params, results.Repertoires[i]));
		}

		// 3. pivot results by target
		
		results.TargetValues = new TargetValues[params.Targets.length];
		for (int itarget = 0; itarget < params.Targets.length; ++itarget) {
			TargetValues values = new TargetValues();
			results.TargetValues[itarget] = values;
			
			values.Target = params.Targets[itarget];
			values.Values = new double[results.Repertoires.length];
			for (int irep = 0; irep < values.Values.length; ++irep) values.Values[irep] = 0.0;
		}

		for (int irep = 0; irep < results.Repertoires.length; ++irep) {

			Repertoire rep = results.Repertoires[irep];
			long[] targetCounts = futures.get(irep).get();

			for (int itarget = 0; itarget < targetCounts.length; ++itarget) {

				double normCount = (rep.isCellfree()
									? rep.getCountPerMilliliter(targetCounts[itarget])
									: rep.getFractionOfCells(targetCounts[itarget]));
				
				results.TargetValues[itarget].Values[irep] = normCount;
			}
		}

		return(results);
	}

	// +---------------------+
	// | matchTargets(Async) |
	// +---------------------+

	// match each target in a single repertoire, returning counts in an
	// array in the same order as params.Targets

	private CompletableFuture<long[]> matchTargetsAsync(Params params, Repertoire rep) {
		return(Exec.runAsync("matchTargets", new Exec.AsyncOperation() {
			public long[] execute() throws Exception {
				return(matchTargets(params, rep));
			}
		}));
	}
	
	private long[] matchTargets(Params params, Repertoire rep) throws Exception {

		InputStream stm = null;
		InputStreamReader reader = null;
		TsvReader tsv = null;

		try {
			stm = params.CRS.getRepertoireStream(rep);
			reader = new InputStreamReader(stm);
			tsv = new TsvReader(reader, 0);

			long[] counts = new long[params.Targets.length];
			for (int i = 0; i < counts.length; ++i) counts[i] = 0L;

			Rearrangement r;

			while ((r = tsv.readNext()) != null) {
				for (int i = 0; i < counts.length; ++i) {
					if (mrdMatch(params.Targets[i], r, cfg.MinMatchLength)) {
						counts[i] += r.Count;
					}
				}
			}
			
			return(counts);	
		}
		finally {
			Utility.safeClose(tsv);
			Utility.safeClose(reader);
			Utility.safeClose(stm);
		}
	}

	// +---------------------+
	// | getDxOptions(Async) |
	// +---------------------+

	public static class RepertoireResultSelections extends RepertoireResult
	{
		public List<Integer> SelectionIndices;
	}

	public CompletableFuture<List<RepertoireResultSelections>>
		getDxOptionsAsync(ContextRepertoireStore crs, String[] repertoires) {
		
		return(Exec.runAsync("getDxOptions", new Exec.AsyncOperation() {
			public List<RepertoireResultSelections> execute() throws Exception {
				return(getDxOptions(crs, repertoires));
			}
		}));
	}

	private List<RepertoireResultSelections> getDxOptions(ContextRepertoireStore crs,
														  String[] repertoires) throws Exception {

		// 1. Get potential dx rearrangements and sort results
		TopXRearrangements.Params topxParams = new TopXRearrangements.Params();
		topxParams.CRS = crs;
		topxParams.Repertoires = repertoires;
		topxParams.Sort = TopXRearrangements.TopXSort.DxPotential;
		topxParams.Count = cfg.DxOptionsCount;

		TopXRearrangements topx = new TopXRearrangements(new TopXRearrangements.Config());
		RepertoireResult[] potentials = topx.getAsync(topxParams).get();
		Arrays.sort(potentials, new RepertoireResultComparator());

		// 2. Add rearrangements that pass threshold and pre-select unique "Dx" clones
		List<RepertoireResultSelections> results = new ArrayList<RepertoireResultSelections>();

		List<Rearrangement> seen = new ArrayList<Rearrangement>();
		
		for (int i = 0; i < potentials.length; ++i) {

			if (potentials[i].Rearrangements.size() == 0) continue;

			RepertoireResultSelections rrs = new RepertoireResultSelections();
			rrs.Repertoire = potentials[i].Repertoire;
			rrs.Rearrangements = new ArrayList<Rearrangement>();
			rrs.SelectionIndices = new ArrayList<Integer>();

			for (int j = 0; j < potentials[i].Rearrangements.size(); ++j) {

				Rearrangement r = potentials[i].Rearrangements.get(j);
				double fractionOfLocus = r.getFractionOfLocus(rrs.Repertoire);
				
				if (r.Dx || fractionOfLocus >= cfg.DxOptionsMinFractionOfLocus) {
					rrs.Rearrangements.add(r);
				}

				if (r.Dx) {

					int iseen = 0;
					while (iseen < seen.size()) {
						if (mrdMatch(r, seen.get(iseen), cfg.MinMatchLength)) break;
						++iseen;
					}
					
					if (iseen == seen.size()) {
						rrs.SelectionIndices.add(rrs.Rearrangements.size() - 1);
						seen.add(r);
					}
				}
			}

			if (rrs.Rearrangements.size() > 0) {
				results.add(rrs);
			}
		}

		return(results);
	}

	// +----------+
	// | mrdMatch |
	// +----------+

	// since mrd tracking is done across multiple assay versions, we detect matches
	// by aligning on the J index and scanning left and right --- if the rearrangements
	// fully match the length of the shorter of the two seqeunces we call it good.
	// At least the last time I looked at it, the Adaptive version did not impose a
	// minimum match, which caused "compression" errors where very short sequences
	// over-matched; so we parameterize that herek. Not if either rearrangment doesn't
	// call a J index, we just match from the J side edge.

	public static boolean mrdMatch(Rearrangement r1, Rearrangement r2, int cchMatchMin) {

		// align on J index (or J edge if necessary)
		int cch1 = r1.Rearrangement.length();
		int cch2 = r2.Rearrangement.length();

		int ichJ1 = (r1.JIndex == -1 || r1.JIndex > cch1 ? cch1 : r1.JIndex);
		int ichJ2 = (r2.JIndex == -1 || r2.JIndex > cch2 ? cch2 : r2.JIndex);

		boolean match = true;
		int cchMatch = 0;
		char ch1, ch2;

		// search right
		int ich1 = ichJ1;
		int ich2 = ichJ2;

		while (ich1 < cch1 && ich2 < cch2) {
			ch1 = Character.toLowerCase(r1.Rearrangement.charAt(ich1));
			ch2 = Character.toLowerCase(r2.Rearrangement.charAt(ich2));
			if (ch1 != ch2) { match = false; break; }
			ich1++; ich2++; cchMatch++;
		}

		if (!match) return(false);
		
		// and left
		ich1 = ichJ1 - 1;
		ich2 = ichJ2 - 1;

		while (ich1 >= 0 && ich2 >= 0) {
			ch1 = Character.toLowerCase(r1.Rearrangement.charAt(ich1));
			ch2 = Character.toLowerCase(r2.Rearrangement.charAt(ich2));
			if (ch1 != ch2) { match = false; break; }
			ich1--; ich2--; cchMatch++;
		}

		// and out
		return(match && cchMatch >= cchMatchMin);
	}

	// +-----------------+
	// | Repertoire Sort |
	// +-----------------+

	public static class RepertoireComparator implements Comparator<Repertoire> {
		public int compare(Repertoire r1, Repertoire r2) {
			return(compareRepertoires(r1, r2));
		};
	}

	public static class RepertoireResultComparator implements Comparator<RepertoireResult> {
		public int compare(RepertoireResult rr1, RepertoireResult rr2) {
			return(compareRepertoires(rr1.Repertoire, rr2.Repertoire));
		};
	}

	public static int compareRepertoires(Repertoire r1, Repertoire r2) {
		if (r1.Date != null && r2.Date != null) return(r1.Date.compareTo(r2.Date));
		if (r1.Date == null && r2.Date != null) return(1);
		if (r1.Date != null && r2.Date == null) return(-1);
		return(r1.Name.compareTo(r2.Name));
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(Tracking.class.getName());
}
