//
// TOPXREARRANGEMENTS.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.InputStreamReader;
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
		FractionOfCount
	}

	public static class Params
	{
		public ContextRepertoireStore CRS;
		public String Repertoire;
		public TopXSort Sort;
		public Integer Count;
	}
	
	// +----------+
	// | getAsync |
	// | get      |
	// +----------+

	public CompletableFuture<RepertoireResult> getAsync(Params params) {

		CompletableFuture<RepertoireResult> future = new CompletableFuture<RepertoireResult>();

		Exec.getPool().submit(() -> {

			RepertoireResult result = new RepertoireResult();
				
			try {
				result = get(params);
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "getAsync", true));
			}
			
			future.complete(result);
		});

		return(future);
	}

	private RepertoireResult get(Params params) throws Exception {

		if (params.Count > cfg.MaxCount) {
			throw new Exception(String.format("TopX count %d above cfg max %d",
											  params.Count, cfg.MaxCount));
		}
		
		Repertoire rep = params.CRS.findRepertoire(params.Repertoire);
		if (rep == null) throw new Exception("Repertoire " + params.Repertoire + " not found");

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
			default: return(null);
		}

		return(cmp);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(TopXRearrangements.class.getName());
}
