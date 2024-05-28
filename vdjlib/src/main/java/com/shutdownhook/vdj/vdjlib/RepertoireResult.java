//
// REPERTOIRERESULT.JAVA
//

package com.shutdownhook.vdj.vdjlib;

import java.util.List;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class RepertoireResult
{
	public Repertoire Repertoire;
	public List<Rearrangement> Rearrangements;

	public String toJson() {
		
		StringBuilder sb = new StringBuilder();

		sb.append("{");
		sb.append("\"Repertoire\": ");
		sb.append(Repertoire.toJson());
		sb.append(",");
		sb.append("\"Rearrangements\": ");
		sb.append(Rearrangement.toJsonArray(Repertoire, Rearrangements));
		sb.append("}");

		return(sb.toString());
	}

	public static String resultsToJson(RepertoireResult[] results) {
		
		StringBuilder sb = new StringBuilder();
		sb.append("[");

		for (int i = 0; i < results.length; ++i) {
			if (i > 0) sb.append(",");
			sb.append(results[i].toJson());
		}
		
		sb.append("]");
		return(sb.toString());
	}
}

