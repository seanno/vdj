//
// REARRANGEMENT.JAVA
// 

package com.shutdownhook.vdj.vdjlib.model;

import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
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

	public double Probability;
	public int[] VSHMIndices;

	// On-Demand calculations

	public double getFractionOfLocus(Repertoire repertoire) {
		return(((double) Count) / ((double) repertoire.LocusCounts.get(Locus.getGroup())));
	}

	public double getFractionOfCount(Repertoire repertoire) {
		return(((double) Count) / ((double) repertoire.TotalCount));
	}

	public double getFractionOfCells(Repertoire repertoire) {
		if (repertoire.TotalCells == 0) return(0.0);
		return(((double) Count / ((double) repertoire.TotalCells)));
	}

	public double getCountPerMilliliter(Repertoire repertoire) {
		if (repertoire.TotalMilliliters == 0.0) return(0.0);
		return(((double) Count) / repertoire.TotalMilliliters);
	}

	// Cached calculations

	public synchronized String getCDR3() {

		if (cdr3 == null) {
			
			int ichStart = (VIndex == -1 ? DIndex : VIndex);
			if (ichStart == -1) ichStart = 0; // bummer
			
			int ichMac = ichStart + Cdr3Length;
			if (ichMac > Rearrangement.length()) ichMac = Rearrangement.length();
			
			cdr3 = Rearrangement.substring(ichStart, ichMac);
		}

		return(cdr3);
	}
	
	private transient String cdr3;

	// JSON Serialization

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
			json.addProperty("CountPerMilliliter", getCountPerMilliliter(repertoire));
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
	
	// vSHM Sub-Serialization
	
	private String vSHMIndicesToCsv() {
		
		if (VSHMIndices == null) return("");
		StringBuilder sb = new StringBuilder();
		for (int i : VSHMIndices) {
			if (sb.length() > 0) sb.append(",");
			sb.append(Integer.toString(VSHMIndices[i]));
		}
		
		return(sb.toString());
	}

	public static int[] VSHMCsvToIndices(String csv) {
		
		if (csv == null || csv.isEmpty()) return(null);

		String[] fields = csv.split(",");
		int[] indices = new int[fields.length];

		for (int i = 0; i < fields.length; ++i) {
			indices[i] = Integer.parseInt(fields[i]);
		}

		return(indices);
	}

	// Members

	private static Gson gson = new Gson();
}
