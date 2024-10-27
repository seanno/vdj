//
// SEARCHER.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.RearrangementKey;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class Searcher
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public Integer MaxResults = 2000; // 0 == no max
	}

	public Searcher(Config cfg) {
		this.cfg = cfg;
	}
	
	// +--------+
	// | Params |
	// +--------+

	public static class Params
	{
		public ContextRepertoireStore CRS;
		public String[] Repertoires;
		public String Motif;
		public RearrangementKey.Extractor Extractor;
		public RearrangementKey.Matcher Matcher;
	}

	// +-------------+
	// | searchAsync |
	// | search      |
	// +-------------+

	public CompletableFuture<RepertoireResult[]> searchAsync(Params params) {
		return(Exec.runAsync("search", new Exec.AsyncOperation() {
			public RepertoireResult[] execute() throws Exception {
				return(search(params));
			}
		}));
	}

	private RepertoireResult[] search(Params params) throws Exception {

		RepertoireResult[] results = new RepertoireResult[params.Repertoires.length];
		
		List<CompletableFuture<RepertoireResult>> futures =
			new ArrayList<CompletableFuture<RepertoireResult>>();

		for (int i = 0; i < params.Repertoires.length; ++i) {
			Repertoire rep = params.CRS.findRepertoire(params.Repertoires[i]);
			if (rep == null) throw new Exception(String.format("rep %s not found", params.Repertoires[i]));
			futures.add(searchOneRepertoireAsync(params, rep));
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();

		for (int i = 0; i < params.Repertoires.length; ++i) {
			results[i] = futures.get(i).get();
		}

		return(results);
	}

	// +---------------------+
	// | searchOneRepertoire |
	// +---------------------+

	public CompletableFuture<RepertoireResult>
		searchOneRepertoireAsync(Params params, Repertoire repertoire) {

		return(Exec.runAsync("searchOne", new Exec.AsyncOperation() {
			public RepertoireResult execute() throws Exception {
				return(searchOneRepertoire(params, repertoire));
			}
		}));
	}

	private RepertoireResult searchOneRepertoire(Params params, Repertoire repertoire)
		throws IOException {

		InputStream stm = null;
		InputStreamReader rdr = null;
		TsvReader tsv = null;

		try {
			stm = params.CRS.getRepertoireStream(repertoire);
			rdr = new InputStreamReader(stm);
			tsv = new TsvReader(rdr, 0);

			RepertoireResult result = new RepertoireResult();
			result.Repertoire = repertoire;
			result.Rearrangements = new ArrayList<Rearrangement>();
			result.Truncated = false;
			
			Rearrangement r;
			
			while ((r = tsv.readNext()) != null) {

				String key = params.Extractor.extract(r);
				if (params.Matcher.matches(params.Motif, key)) {

					if (cfg.MaxResults != 0 && result.Rearrangements.size() == cfg.MaxResults) {
						result.Truncated = true;
						break;
					}

					result.Rearrangements.add(r);
				}
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
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(Searcher.class.getName());
}
