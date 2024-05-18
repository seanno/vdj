//
// REPERTOIRE.JAVA
// 

package com.shutdownhook.vdj.vdjlib.model;

import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson;

public class Repertoire
{
	// Members

	public String Name;
	public long TotalCells = 0;
	public long TotalCount = 0;
	public long TotalUniques = 0;
	public Map<Locus,Long> LocusCounts = new HashMap<Locus,Long>();

	// Helpers

	public void accumulateCount(Locus locus, long count) {

		TotalCount += count;
		TotalUniques += 1;
		
		LocusCounts.put(locus, (LocusCounts.containsKey(locus)
								? LocusCounts.get(locus) + count
								: count));
	}

	// Serialization

	public static Repertoire fromJson(String json) {
		return(gson.fromJson(json, Repertoire.class));
	}

	public static Repertoire[] fromJsonArray(String json) {
		return(gson.fromJson(json, Repertoire[].class));
	}

	public String toJson() {
		return(gson.toJson(this));
	}

	public static String toJsonArray(Repertoire[] reps) {
		return(gson.toJson(reps));
	}
	
	private static Gson gson = new Gson();
}
