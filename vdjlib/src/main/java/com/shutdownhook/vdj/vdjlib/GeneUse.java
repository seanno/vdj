//
// GENEUSE.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.model.Repertoire;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class GeneUse
{
	// +--------+
	// | Params |
	// +--------+
	
	public static class Params
	{
		public ContextRepertoireStore CRS;
		public String Repertoire;
		public Boolean IncludeUnknown;
		public Boolean IncludeFamilyOnly;
	}

	// +--------+
	// | Result |
	// +--------+

	// This return structure is kind of weird, each vjc point is at the
	// same index in each array. This is the form that our current charting
	// library (plotly) wants, so creating it here avoids transformation
	// at the client.
	
	public static class Result
	{
		public String[] VGenes;
		public String[] JGenes;
		public long[] Counts;
	}

	// +--------+
	// | VJPair |
	// +--------+

	public static class VJPair
	{
		public VJPair(String v, String j, long count) {
			this.V = v;
			this.J = j;
			this.Count = count;
		}

		public String V;
		public String J;
		public long Count;
	}
	
	// +------------+
	// | get(Async) |
	// +------------+

	public CompletableFuture<Result> getAsync(Params params) {
		return(Exec.runAsync("GeneUse", new Exec.AsyncOperation() {
			public Result execute() throws Exception {
				return(get(params));
			}
		}));
	}
	
	private Result get(Params params) throws Exception {

		Repertoire rep = params.CRS.findRepertoire(params.Repertoire);
		if (rep == null) throw new Exception("Repertoire " + params.Repertoire + " not found");

		InputStream stm = null;
		InputStreamReader rdr = null;
		TsvReader tsv = null;

		try {
			stm = params.CRS.getRepertoireStream(rep);
			rdr = new InputStreamReader(stm);
			tsv = new TsvReader(rdr, 0);

			Map<String,VJPair> counts = new HashMap<String,VJPair>();
			Rearrangement r;
			
			while ((r = tsv.readNext()) != null) {

				String v = normalizeGene(r.VResolved, params);
				if (v == null) continue;
				
				String j = normalizeGene(r.JResolved, params);
				if (j == null) continue;

				String key = vjKey(v, j);
				VJPair pair = counts.get(key);
				
				if (pair == null) {
					pair = new VJPair(v, j, r.Count);
					counts.put(key, pair);
				}
				else {
					pair.Count += r.Count;
				}
			}

			String[] keys = counts.keySet().toArray(new String[counts.size()]);
			Arrays.sort(keys);
			
			Result result = new Result();
			result.VGenes = new String[keys.length];
			result.JGenes = new String[keys.length];
			result.Counts = new long[keys.length];


			for (int i = 0; i < keys.length; ++i) {
				VJPair pair = counts.get(keys[i]);
				result.VGenes[i] = pair.V;
				result.JGenes[i] = pair.J;
				result.Counts[i] = pair.Count;
			}
			
			return(result);
		}
		finally {
			if (tsv != null) Utility.safeClose(tsv);
			if (rdr != null) Utility.safeClose(rdr);
			if (stm != null) Utility.safeClose(stm);
		}
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static String vjKey (String v, String j) {
		return(v + "|" + j);
	}
	
	private static String normalizeGene(String resolved, Params params) {

		// check for unknown
		String norm = (resolved == null ? "" : resolved.trim());
		if (norm.isEmpty()) return(params.IncludeUnknown ? "X" : null);

		// remove allele
		int ichAllele = norm.lastIndexOf("*");
		if (ichAllele != -1) norm = norm.substring(0, ichAllele);

		// check for resolved gene
		int ichGene = norm.lastIndexOf("-");
		if (ichGene == -1) return(params.IncludeFamilyOnly ? norm + "-X" : null);

		// yay
		return(norm);
	}

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(GeneUse.class.getName());
}
