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
		public Integer MaxTargets = 50;
		public Integer DxOptionsCount = 20;
		public Double DxOptionsMinFractionOfLocus = .05;
	}
	
	public Tracking(Config cfg, MrdEngine.Config cfgMrd) {
		this.cfg = cfg;
		this.mrd = new MrdEngine(cfgMrd);
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
		public long[] Values; // in order of Repertoires
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
			values.Values = new long[results.Repertoires.length];
		}

		for (int irep = 0; irep < results.Repertoires.length; ++irep) {

			long[] targetCounts = futures.get(irep).get();

			for (int itarget = 0; itarget < targetCounts.length; ++itarget) {
				results.TargetValues[itarget].Values[irep] = targetCounts[itarget];
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
					if (mrd.match(params.Targets[i], r)) {
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

	public CompletableFuture<RepertoireResult[]>
		getDxOptionsAsync(ContextRepertoireStore crs, String[] repertoires) {
		
		return(Exec.runAsync("getDxOptions", new Exec.AsyncOperation() {
			public RepertoireResult[] execute() throws Exception {
				return(getDxOptions(crs, repertoires));
			}
		}));
	}

	private RepertoireResult[] getDxOptions(ContextRepertoireStore crs,
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
		List<RepertoireResult> results = new ArrayList<RepertoireResult>();

		List<Rearrangement> seen = new ArrayList<Rearrangement>();
		
		for (int i = 0; i < potentials.length; ++i) {

			if (potentials[i].Rearrangements.size() == 0) continue;

			RepertoireResult rr = new RepertoireResult();
			rr.Repertoire = potentials[i].Repertoire;
			rr.Rearrangements = new ArrayList<Rearrangement>();
			rr.SelectionIndices = new ArrayList<Integer>();

			for (int j = 0; j < potentials[i].Rearrangements.size(); ++j) {

				Rearrangement r = potentials[i].Rearrangements.get(j);
				double fractionOfLocus = r.getFractionOfLocus(rr.Repertoire);
				
				if (r.Dx || fractionOfLocus >= cfg.DxOptionsMinFractionOfLocus) {
					rr.Rearrangements.add(r);
				}

				if (r.Dx) {

					int iseen = 0;
					while (iseen < seen.size()) {
						if (mrd.match(r, seen.get(iseen))) break;
						++iseen;
					}
					
					if (iseen == seen.size()) {
						rr.SelectionIndices.add(rr.Rearrangements.size() - 1);
						seen.add(r);
					}
				}
			}

			if (rr.Rearrangements.size() > 0) {
				results.add(rr);
			}
		}

		return(results.toArray(new RepertoireResult[results.size()]));
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
	private MrdEngine mrd;
	
	private final static Logger log = Logger.getLogger(Tracking.class.getName());
}
