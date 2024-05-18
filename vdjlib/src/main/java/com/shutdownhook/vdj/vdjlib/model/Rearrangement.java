//
// REARRANGEMENT.JAVA
// 

package com.shutdownhook.vdj.vdjlib.model;

import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.Gson;

public class Rearrangement
{
	// Members
	
	public String Rearrangement;
	public String AminoAcid;
	public FrameType FrameType;
	public Locus Locus;

	public long Count;
	
	public String VResolved;
	public String DResolved;
	public String JResolved;

	public int Cdr3Length;
	public int VIndex;
	public int DIndex;
	public int JIndex;
	public int N1Index;
	public int N2Index;

	public int[] VSHMIndices;

	// On-Demand calculations

	public double getFractionOfLocus(Repertoire repertoire) {
		return(((double) Count) / ((double) repertoire.LocusCounts.get(Locus)));
	}

	public double getFractionOfCount(Repertoire repertoire) {
		return(((double) Count) / ((double) repertoire.TotalCount));
	}

	public double getFractionOfCells(Repertoire repertoire) {
		if (repertoire.TotalCells == 0) return(0.0);
		return(((double) Count / ((double) repertoire.TotalCells)));
	}

	// Serialization

	public static Rearrangement fromJson(String json) {
		return(gson.fromJson(json, Rearrangement.class));
	}

	public String toJson() {
		return(toJson(null));
	}
	
	public String toJson(Repertoire repertoire) {

		JsonObject json = (JsonObject) gson.toJsonTree(this);

		if (repertoire != null) {
			json.addProperty("FractionOfLocus", getFractionOfLocus(repertoire));
			json.addProperty("FractionOfCount", getFractionOfCount(repertoire));
			json.addProperty("FractionOfCells", getFractionOfCells(repertoire));
		}
		
		return(json.toString());
	}

	public static String toJsonArray(Repertoire repertoire, List<Rearrangement> rearrangements) {
		
		StringBuilder sb = new StringBuilder();
		sb.append("[");

		for (int i = 0; i < rearrangements.size(); ++i) {
			if (i > 0) sb.append(",");
			sb.append(rearrangements.get(i).toJson(repertoire));
		}

		sb.append("]");
		return(sb.toString());
	}

	private static Gson gson = new Gson();
}
