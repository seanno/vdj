//
// TOPXREARRANGEMENTS.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.model.Repertoire;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class TopXRearrangements
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public Integer MaxCount = 500;
	}
	
	public TopXRearrangements(Config cfg) {
		this.cfg = cfg;
	}

	// +--------+
	// | Params |
	// +--------+
	
	public static enum TopXSort
	{
		Count,
		FractionOfCells,
		FractionOfLocus,
		FractionOfCount,
		DxPotential
	}

	public static class Params
	{
		public ContextRepertoireStore CRS;
		public String[] Repertoires;
		public TopXSort Sort;
		public Integer Count;
	}
	
	// +----------+
	// | getAsync |
	// +----------+

	public CompletableFuture<RepertoireResult[]> getAsync(Params params) {
		return(Exec.runAsync("TopX", new Exec.AsyncOperation() {
			public RepertoireResult[] execute() throws Exception {
				return(get(params));
			}
		}));
	}
	private RepertoireResult[] get(Params params) throws Exception {

		RepertoireResult[] results = new RepertoireResult[params.Repertoires.length];
		
		List<CompletableFuture<RepertoireResult>> futures =
			new ArrayList<CompletableFuture<RepertoireResult>>();

		for (int i = 0; i < params.Repertoires.length; ++i) {
			futures.add(getOneAsync(params, i));
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();

		for (int i = 0; i < params.Repertoires.length; ++i) {
			results[i] = futures.get(i).get();
		}

		return(results);
	}
	

	// +-------------+
	// | getOneAsync |
	// | getOne      |
	// +-------------+

	private CompletableFuture<RepertoireResult> getOneAsync(Params params, int irep) {
		return(Exec.runAsync("TopXOne", new Exec.AsyncOperation() {
			public RepertoireResult execute() throws Exception {
				return(getOne(params, irep));
			}
		}));
	}

	private RepertoireResult getOne(Params params, int irep) throws Exception {

		if (params.Count > cfg.MaxCount) {
			throw new Exception(String.format("TopX count %d above cfg max %d",
											  params.Count, cfg.MaxCount));
		}
		
		Repertoire rep = params.CRS.findRepertoire(params.Repertoires[irep]);
		if (rep == null) throw new Exception("Repertoire " + params.Repertoires[irep] + " not found");

		Comparator<Rearrangement> cmp = getComparator(params.Sort, rep);

		InputStream stm = null;
		InputStreamReader rdr = null;
		TsvReader tsv = null;

		try {
			stm = params.CRS.getRepertoireStream(rep);
			rdr = new InputStreamReader(stm);
			tsv = new TsvReader(rdr, 0);

			RepertoireResult result = new RepertoireResult();
			result.Repertoire = rep;
			result.Rearrangements = new LinkedList<Rearrangement>();

			Rearrangement r;
			
			while ((r = tsv.readNext()) != null) {
				addtoList(result.Rearrangements, r, cmp, params.Count);
			}

			return(result);
		}
		finally {
			if (tsv != null) Utility.safeClose(tsv);
			if (rdr != null) Utility.safeClose(rdr);
			if (stm != null) Utility.safeClose(stm);
		}
	}

	private static void addtoList(List<Rearrangement> rearrangements, Rearrangement r,
								  Comparator<Rearrangement> cmp, int maxSize) {
		
		// walk from the end of the list looking for a spot where we are greater.
		// this backwards walk is intentional because we'll typically break out
		// much more quickly than if we walked forward looking for one smaller.
				
		int i = rearrangements.size() - 1;
		while (i >= 0 && cmp.compare(r, rearrangements.get(i)) > 0) {
			--i;
		}

		// i is now at the first position where the value is < that r (may be
		// -1 indicating we're the largest found so far). Insert to the right of
		// that position (unless it's beyond the end and we already have
		// maxSize entries)

		++i;

		if (rearrangements.size() < maxSize) {
			// add and all good, we're still building
			rearrangements.add(i, r);
		}
		else if (i < rearrangements.size()) {
					
			// add at correct position
			rearrangements.add(i, r);

			// maybe trim
			if (rearrangements.size() > maxSize) {
				rearrangements.remove(rearrangements.size() - 1);
			}
		}
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private static Comparator<Rearrangement> getComparator(TopXSort sort, Repertoire rep) {

		Comparator<Rearrangement> cmp = null;
		
		switch (sort) {
			case Count:           cmp = Comparator.comparingLong(r -> r.Count); break;
			case FractionOfCells: cmp = Comparator.comparingDouble(r -> r.getFractionOfCells(rep)); break;
			case FractionOfLocus: cmp = Comparator.comparingDouble(r -> r.getFractionOfLocus(rep)); break;
			case FractionOfCount: cmp = Comparator.comparingDouble(r -> r.getFractionOfCount(rep)); break;
			case DxPotential:     cmp = new DxPotentialComparator(rep); break;
			default: return(null);
		}

		return(cmp);
	}

	public static class DxPotentialComparator implements Comparator<Rearrangement>
	{
		public DxPotentialComparator(Repertoire rep) {
			this.rep = rep;
		}
		
		public int compare(Rearrangement r1, Rearrangement r2) {

			// nulls
			if (r1 == null && r2 == null) return(0);
			if (r1 == null) return(1);
			if (r2 == null) return(-1);

			// dx
			if (r1.Dx && !r2.Dx) return(1);
			if (!r1.Dx && r2.Dx) return(-1);

			// fraction of locus
			double dbl1 = r1.getFractionOfLocus(rep);
			double dbl2 = r2.getFractionOfLocus(rep);

			if (dbl1 < dbl2) return(-1);
			if (dbl1 > dbl2) return(1);
			return(0);
		}

		private Repertoire rep;
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(TopXRearrangements.class.getName());
}
