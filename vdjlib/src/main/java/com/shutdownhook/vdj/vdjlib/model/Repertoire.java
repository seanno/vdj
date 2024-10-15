//
// REPERTOIRE.JAVA
// 

package com.shutdownhook.vdj.vdjlib.model;

import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;

import com.shutdownhook.vdj.vdjlib.Utility;

public class Repertoire
{
	// Members

	public String Name;
	public LocalDate Date;
	public long TotalCells = 0;
	public long TotalCount = 0;
	public long TotalUniques = 0;
	public double TotalMilliliters = 0.0;
	public Map<LocusGroup,Long> LocusCounts = new HashMap<LocusGroup,Long>();

	// normalizers
	
	public boolean isCellfree() { return(TotalMilliliters > 0.0); }
	
	public double getFractionOfLocus(long count, Locus locus) {
		return(((double) count) / ((double) LocusCounts.get(locus.getGroup())));
	}

	public double getFractionOfCount(long count) {
		return(((double) count) / ((double) TotalCount));
	}

	public double getFractionOfCells(long count) {
		if (TotalCells == 0) return(0.0);
		return(((double) count / ((double) TotalCells)));
	}

	public double getCountPerMilliliter(long count) {
		if (TotalMilliliters == 0.0) return(0.0);
		return(((double) count) / TotalMilliliters);
	}

	// Helpers

	public void accumulateCount(Locus locus, long count) {

		TotalCount += count;
		TotalUniques += 1;

		LocusGroup group = locus.getGroup();
		
		LocusCounts.put(group, (LocusCounts.containsKey(group)
								? LocusCounts.get(group) + count
								: count));
	}

	// Serialization

	public static Repertoire fromJson(String json) {
		return(Utility.getGson().fromJson(json, Repertoire.class));
	}

	public static Repertoire[] fromJsonArray(String json) {
		return(Utility.getGson().fromJson(json, Repertoire[].class));
	}

	public String toJson() {
		return(Utility.getGson().toJson(this));
	}

	public static String toJsonArray(Repertoire[] reps) {
		return(Utility.getGson().toJson(reps));
	}

	// Array mgmt

	public static Repertoire[] append(Repertoire[] oldReps, Repertoire newRep) {

		Repertoire[] newReps = new Repertoire[oldReps.length + 1];

		for (int i = 0; i < oldReps.length; ++i) newReps[i] = oldReps[i];
		newReps[oldReps.length] = newRep;

		return(newReps);
	}
	
	public static Repertoire[] remove(Repertoire[] oldReps, Repertoire rep) {
		return(remove(oldReps, rep.Name));
	}

	public static Repertoire[] remove(Repertoire[] oldReps, String name) {

		Repertoire[] newReps = new Repertoire[oldReps.length - 1];

		int iNew = 0;
		boolean found = false;

		for (int iOld = 0; iOld < oldReps.length; ++iOld) {

			if (oldReps[iOld].Name.equals(name)) {
				found = true;
			}
			else {
				// this length check is a bit odd, but it protects the
				// degenerate case where rep isn't found in the list. In
				// that case we'd overwrite the bounds of newReps even
				// though we're not going to use it.
				if (iNew < newReps.length) newReps[iNew++] = oldReps[iOld];
			}
		}

		return(found ? newReps : oldReps);
	}

	public static Repertoire find(Repertoire[] reps, String name) {

		for (int i = 0; i < reps.length; ++i) {
			if (reps[i].Name.equals(name)) return(reps[i]);
		}

		return(null);
	}

}
