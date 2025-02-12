//
// LOCUS.JAVA
// 

package com.shutdownhook.vdj.vdjlib.model;

public enum Locus
{
	TCRAD(LocusGroup.TCRAD),
	TCRB(LocusGroup.TCRB),
	TCRG(LocusGroup.TCRG),
	IGH(LocusGroup.IGH),
	DJ(LocusGroup.IGH),
	IGK(LocusGroup.IGKL),
	IGL(LocusGroup.IGKL);

	private Locus(LocusGroup group) {
		this.group = group;
	}

	private LocusGroup group;
	public LocusGroup getGroup() { return(group); }

	public static Locus fromGene(String v, String d, String j,
								 String vTies, String dTies, String jTies) throws IllegalArgumentException {

		String gene = (hasLocus(j) ? j
					   : (hasLocus(d) ? d
						  : (hasLocus(v) ? v
							 : (hasLocus(jTies) ? jTies
								: (hasLocus(dTies) ? dTies
								   : vTies)))));

		if (gene.startsWith("TCR")) {
			switch (gene.substring(3, 4)) {
				case "B": return(Locus.TCRB);
				case "G": return(Locus.TCRG);
				default: return(Locus.TCRAD);
			}
		}
		else if (gene.startsWith("IG")) {
			switch (gene.substring(2, 3)) {
				case "H": return((v.isEmpty() && vTies.isEmpty()) ? Locus.DJ : Locus.IGH);
				case "K": return(Locus.IGK);
				default: return(Locus.IGL);
			}
		}
		throw new IllegalArgumentException("Bad Locus: " + gene);
	}

	private static boolean hasLocus(String str) {
		return(str != null &&
			   !str.isEmpty() &&
			   (str.startsWith("TCR") || str.startsWith("IG")));
	}
}

