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

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.vdj.vdjlib.model.Repertoire;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class Searcher
{
	// +--------------+
	// | SearchParams |
	// +--------------+

	public static class SearchParams
	{
		public RepertoireStore Store;
		public String UserId;
		public String Context;
		public String[] Repertoires;
		public String Motif;
		public Boolean MotifIsAA = false;
		public Integer AllowedMutations = 0;
	}

	// +-------------+
	// | searchAsync |
	// | search      |
	// +-------------+

	public static CompletableFuture<RepertoireResult[]> searchAsync(SearchParams params) {

		CompletableFuture<RepertoireResult[]> future = new CompletableFuture<RepertoireResult[]>();

		Exec.getPool().submit(() -> {

			RepertoireResult[] results = null;
				
			try {
				results = search(params);
			}
			catch (Exception e) {
				log.warning(Easy.exMsg(e, "searchAsync", true));
			}
			
			future.complete(results);
		});

		return(future);
	}

	private static RepertoireResult[] search(SearchParams params) throws Exception {

		Repertoire[] repertoires = params.Store.getContextRepertoires(params.UserId, params.Context);

		RepertoireResult[] results = new RepertoireResult[params.Repertoires.length];
		
		List<CompletableFuture<List<Rearrangement>>> futures =
			new ArrayList<CompletableFuture<List<Rearrangement>>>();

		for (int i = 0; i < params.Repertoires.length; ++i) {

			results[i] = new RepertoireResult();
			results[i].Repertoire = find(repertoires, params.Repertoires[i]);

			if (results[i].Repertoire == null) {
				throw new Exception(String.format("Repertoire %s not found", params.Repertoires[i]));
			}
			
			futures.add(searchOneRepertoireAsync(params, params.Repertoires[i]));
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();

		for (int i = 0; i < params.Repertoires.length; ++i) {
			results[i].Rearrangements = futures.get(i).get();
		}

		return(results);
	}

	private static Repertoire find(Repertoire[] repertoires, String name) {
		
		for (Repertoire r : repertoires) {
			if (r.Name.equals(name)) return(r);
		}
		
		return(null);
	}
	
	// +---------------------+
	// | searchOneRepertoire |
	// +---------------------+

	public static CompletableFuture<List<Rearrangement>>
		searchOneRepertoireAsync(SearchParams params, String repertoire) {

		CompletableFuture<List<Rearrangement>> future = 
			new CompletableFuture<List<Rearrangement>>();

		Exec.getPool().submit(() -> {
			List<Rearrangement> results = null;
			try {
				results = searchOneRepertoire(params, repertoire);
			}
			catch (Exception e) {
				log.warning(Easy.exMsg(e, "searchOneRepertoireAsync", true));
			}
			
			future.complete(results);
		});

		return(future);
	}

	private static List<Rearrangement> searchOneRepertoire(SearchParams params, String repertoire)
		throws IOException {

		InputStream stm = null;
		InputStreamReader rdr = null;
		TsvReader tsv = null;

		try {
			stm = params.Store.getRepertoireStream(params.UserId, params.Context, repertoire);
			rdr = new InputStreamReader(stm);
			tsv = new TsvReader(rdr, 0);

			List<Rearrangement> rearrangements = new ArrayList<Rearrangement>();

			Rearrangement r;
			
			while ((r = tsv.readNext()) != null) {
				
				if (matches(params.MotifIsAA ? r.AminoAcid : r.Rearrangement,
							params.Motif, params.AllowedMutations)) {
					
					rearrangements.add(r);
				}
			}

			return(rearrangements);
		}
		finally {
			if (tsv != null) Easy.safeClose(tsv);
			if (rdr != null) Easy.safeClose(rdr);
			if (stm != null) Easy.safeClose(stm);
		}
	}

	// +---------+
	// | matches |
	// +---------+

	public static boolean matches(String input, String motif, int allowedMutations) {

		if (Easy.nullOrEmpty(input)) return(false);
		if (Easy.nullOrEmpty(motif)) return(false);
		
		int ichStart = 0;
		int cchMotif = motif.length();
		int ichInputMac = input.length() - cchMotif + 1;

		if (ichInputMac <= 0) return(false);
		
		while (ichStart < ichInputMac) {

			int mutsRemaining = allowedMutations;
			int j = 0;
			while (j < cchMotif) {
				
				if (input.charAt(ichStart + j) != motif.charAt(j)) {
					if (mutsRemaining == 0) break;
					--mutsRemaining;
				}
				
				++j;
			}
			
			if (j == cchMotif) return(true);
			
			++ichStart;
		}

		return(false);
	}
	
	private final static Logger log = Logger.getLogger(Searcher.class.getName());
}
