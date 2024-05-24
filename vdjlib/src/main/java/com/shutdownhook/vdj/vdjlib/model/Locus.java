//
// FRAMETYPE.JAVA
// 

package com.shutdownhook.vdj.vdjlib.model;

public enum Locus
{
	TCRAD,
	TCRB,
	TCRG,
	IGH,
	IGKL;

	public static Locus fromGene(String v, String d, String j) throws IllegalArgumentException {

		String gene = (!j.isEmpty() ? j : (!d.isEmpty() ? d : v));

		if (gene.startsWith("TCR")) {
			switch (gene.substring(3, 4)) {
				case "B": return(Locus.TCRB);
				case "G": return(Locus.TCRG);
				default: return(Locus.TCRAD);
			}
		}
		else if (gene.startsWith("IG")) {
			switch (gene.substring(2, 3)) {
				case "H": return(Locus.IGH);
				default: return(Locus.IGKL);
			}
		}
		throw new IllegalArgumentException("Bad Locus: " + gene);
	}
}

