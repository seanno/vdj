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
import com.shutdownhook.vdj.vdjlib.RearrangementKey.Extractor;
import com.shutdownhook.vdj.vdjlib.RearrangementKey.Matcher;

public class GeneUse
{
	// +--------+
	// | Params |
	// +--------+
	
	public static class Params
	{
		public ContextRepertoireStore CRS;
		public String Repertoire;
	}

	// +--------+
	// | VJPair |
	// +--------+

	public static class VJPair implements Comparable<VJPair>
	{
		public VJPair(String v, String j, long count) {
			this.V = v;
			this.J = j;
			this.Count = count;
			this.Uniques = 1;
		}

		public void accumulate(long count) {
			this.Count += count;
			this.Uniques++;
		}
		
		public int compareTo(VJPair other) {
			int cmp = V.compareTo(other.V);
			if (cmp == 0) cmp = J.compareTo(other.J);
			return(cmp);
		}

		public boolean equals(Object other) {
			if (other == null) return(false);
			if (!(other instanceof VJPair)) return(false);
			if (!V.equals(((VJPair)other).V)) return(false);
			if (!J.equals(((VJPair)other).J)) return(false);
			return(true);
		}

		public String V;
		public String J;
		public long Count;
		public long Uniques;
	}
	
	// +------------+
	// | get(Async) |
	// +------------+

	public CompletableFuture<VJPair[]> getAsync(Params params) {
		return(Exec.runAsync("GeneUse", new Exec.AsyncOperation() {
			public VJPair[] execute() throws Exception {
				return(get(params));
			}
		}));
	}
	
	private VJPair[] get(Params params) throws Exception {

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
					pair.accumulate(r.Count);
				}
			}

			VJPair[] result = counts.values().toArray(new VJPair[counts.size()]);
			Arrays.sort(result);
			
			return(result);
		}
		finally {
			if (tsv != null) Utility.safeClose(tsv);
			if (rdr != null) Utility.safeClose(rdr);
			if (stm != null) Utility.safeClose(stm);
		}
	}

	// +---------------------+
	// | Extractor / Matcher |
	// +---------------------+

	public static Extractor getExtractor() {
		
		return(new Extractor() {
			public String extract(Rearrangement r) {
				return((r.VResolved == null ? "" : r.VResolved) + "," +
					   (r.DResolved == null ? "" : r.DResolved) + "," +
					   (r.JResolved == null ? "" : r.JResolved));
			}
		});
	}

	public static Matcher getMatcher() {
		
		return(new Matcher() {
			public boolean matches(String search, String key) {

				if (search == null || search.isEmpty()) return(false);
				if (key == null || key.isEmpty()) return(false);

				int ichWalk = 0;
				while (ichWalk < search.length()) {
					int ichNextComma = search.indexOf(',', ichWalk);
					if (ichNextComma == -1) ichNextComma = search.length();
					String sub = search.substring(ichWalk, ichNextComma).trim();
					if (!sub.isEmpty() && key.indexOf(sub) == -1) return(false);
					ichWalk = ichNextComma + 1; 
				}

				return(true);
			}
		});
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
		if (norm.isEmpty()) return("X");

		// remove allele
		int ichAllele = norm.lastIndexOf("*");
		if (ichAllele != -1) norm = norm.substring(0, ichAllele);

		// check for resolved gene
		int ichGene = norm.lastIndexOf("-");
		if (ichGene == -1) return(norm + "-X");

		// yay
		return(norm);
	}

	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(GeneUse.class.getName());
}
