//
// SIDELOADEDTSV.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;

import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class SideLoadedTsv
{
	public final static int V2 = 0;
	public final static int V3 = 1;
	public final static int PIPELINE = 2;

	public static int[][] COLS = {
		// in assertRearrangement order
		{ 0, 1, 38, 2, 5, 12, 19, 4, 32, 34, 36, 33, 35, 49, 9, 16, 23 },
		{ 52, 53, 54, 56, 96, 97, 98, 60, 76, 79, 80, 77, 78, 92, 81, 84, 87 },
		{ 0, 1, 42, 4, 43, 45, 46, 8, 33, 35, 37, 36, 34, 69, 12, 18, 24 }
	};

	public static int[][] CELL_COLS = {
		{ -1, -1 },
		{ 37, 35 },
		{ -1, -1 }
	};

	public SideLoadedTsv(String resourceName, int ver) throws IOException {
		
		this.repertoire = new Repertoire();
		this.repertoire.Name = resourceName;
		
		this.matrix = new ArrayList<String[]>();
		this.ver = ver;

		this.load();
	}

	private Repertoire repertoire;
	private List<String[]> matrix;
	private int ver;

	public String getResourceName() { return(repertoire.Name); }
	public Repertoire getRepertoire() { return(repertoire); }

	// +-----------+
	// | Test TSVs |
	// +-----------+

	public static int TEST_V2_TCRB = 0;
	public static int TEST_V3_TCRB = 1;
	public static int TEST_V2_IGH = 2;
	public static int TEST_PIPELINE_TCRG = 3;

	public static SideLoadedTsv getTsv(int which) throws Exception {
		ensureTestTsvs();
		return(tsvs[which]);
	}
	
	private static void ensureTestTsvs() throws Exception {
		
		if (tsvs != null) return;

		tsvs = new SideLoadedTsv[4];
		tsvs[TEST_V2_TCRB] = new SideLoadedTsv("subject9-v2.tsv", V2);
		tsvs[TEST_V3_TCRB] = new SideLoadedTsv("subject9-v3.tsv", V3);
		tsvs[TEST_V2_IGH] = new SideLoadedTsv("02583-02BH.tsv", V2);
		tsvs[TEST_PIPELINE_TCRG] = new SideLoadedTsv("A_TCRG_ID.tsv", PIPELINE);
	}

	private static SideLoadedTsv[] tsvs = null;
	
	// +---------------------+
	// | assertRearrangement |
	// +---------------------+

	public void assertRearrangement(Rearrangement r, int irow) {

		String[] truth = matrix.get(irow + 1);
		int[] cols = COLS[ver];
		
		Assert.assertEquals(truth[cols[0]], r.Rearrangement);
		Assert.assertEquals(truth[cols[1]], r.AminoAcid);
		Assert.assertEquals(truth[cols[2]], r.FrameType.toString());
		Assert.assertEquals(truth[cols[3]], Long.toString(r.Count));

		Assert.assertEquals(truth[cols[4]], r.VResolved);
		Assert.assertEquals(truth[cols[5]], r.DResolved);
		Assert.assertEquals(truth[cols[6]], r.JResolved);
		Assert.assertEquals(truth[cols[7]], Integer.toString(r.Cdr3Length));
		Assert.assertEquals(truth[cols[8]], Integer.toString(r.VIndex));
		Assert.assertEquals(truth[cols[9]], Integer.toString(r.DIndex));
		Assert.assertEquals(truth[cols[10]], Integer.toString(r.JIndex));
		Assert.assertEquals(truth[cols[11]], Integer.toString(r.N1Index));
		Assert.assertEquals(truth[cols[12]], Integer.toString(r.N2Index));

		Assert.assertEquals(locusFromGene(r.VResolved, r.DResolved, r.JResolved,
										  truth[cols[14]], truth[cols[15]], truth[cols[16]]), r.Locus);
		
		int[] vSHMIndices = r.VSHMIndices;
		if (vSHMIndices != null) {
			String[] csv = truth[cols[13]].trim().split(",");
			Assert.assertEquals(csv.length, vSHMIndices.length);
			for (int i = 0; i < csv.length; ++i) {
				Assert.assertEquals(csv[i].trim(), Integer.toString(vSHMIndices[i]));
			}
		}
	}

	// +------------------+
	// | assertRepertoire |
	// +------------------+

	public void assertRepertoire(Repertoire r) {

		Assert.assertEquals(repertoire.Name, r.Name);
		Assert.assertEquals(repertoire.TotalCells, r.TotalCells);
		Assert.assertEquals(repertoire.TotalCount, r.TotalCount);
		Assert.assertEquals(repertoire.TotalUniques, r.TotalUniques);

		Assert.assertEquals(repertoire.LocusCounts.size(), r.LocusCounts.size());
		
		for (Locus locus : repertoire.LocusCounts.keySet()) {
			Assert.assertEquals(repertoire.LocusCounts.get(locus), r.LocusCounts.get(locus));
		}
	}

	// +------+
	// | load |
	// +------+
	
	private void load() throws IOException {

		InputStream resInputStream = this.getClass()
			.getClassLoader().getResourceAsStream(repertoire.Name);
		
		InputStreamReader resStreamReader = new InputStreamReader(resInputStream);
		BufferedReader resBufferedReader = new BufferedReader(resStreamReader);

		String line;

		while ((line = resBufferedReader.readLine()) != null) {

			if (line.isEmpty()) continue;

			if (line.startsWith("#")) {

				if (line.startsWith("#estTotalNucleatedCells")) {
					int ich = line.indexOf("=");
					repertoire.TotalCells = (long) Double.parseDouble(line.substring(ich+1));
				}
				else if (line.startsWith("#productionPCRAmountofTemplate") &&
						 repertoire.TotalCells == 0L) {

					int ich = line.indexOf("=");
					double amt = Double.parseDouble(line.substring(ich+1));
					if (amt >= 12.5) repertoire.TotalCells = (long) (amt * 6.5 / 1000.0);
				}
				
				continue;
			}
			
			String[] fields = line.split("\t");

			matrix.add(fields);

			// skip the header row
			if (matrix.size() > 1) {

				// grab cell count if available
				if (matrix.size() == 2) {
					if (CELL_COLS[ver][0] != -1) {
						String s = fields[CELL_COLS[ver][0]].trim();
						if (!s.isEmpty()) repertoire.TotalCells = Long.parseLong(s);
					}
					if (repertoire.TotalCells == 0 && CELL_COLS[ver][1] != -1) {
						String s = fields[CELL_COLS[ver][1]].trim();
						if (!s.isEmpty()) repertoire.TotalCells = Long.parseLong(s);
					}
				}
				
				String v = fields[COLS[ver][4]];
				String d = fields[COLS[ver][5]];
				String j = fields[COLS[ver][6]];

				String vTies = fields[COLS[ver][14]];
				String dTies = fields[COLS[ver][15]];
				String jTies = fields[COLS[ver][16]];
				
				long c = Long.parseLong(fields[COLS[ver][3]]);
				repertoire.accumulateCount(locusFromGene(v,d,j,vTies,dTies,jTies), c);
			}
		}
		
		resBufferedReader.close();
		resStreamReader.close();
		resInputStream.close();
	}

	// +---------+
	// | Helpers |
	// +---------+
	
	private static Locus locusFromGene(String v, String d, String j,
									   String vTies, String dTies, String jTies) {

		String gene = j;
		if (gene.isEmpty()) gene = d;
		if (gene.isEmpty()) gene = v;
		if (gene.isEmpty()) gene = jTies;
		if (gene.isEmpty()) gene = dTies;
		if (gene.isEmpty()) gene = vTies;
		
		if (gene.startsWith("TCRB")) return(Locus.TCRB);
		if (gene.startsWith("TCRG")) return(Locus.TCRG);
		if (gene.startsWith("TCRAD")) return(Locus.TCRAD);
		if (gene.startsWith("IGH")) return(Locus.IGH);
		if (gene.startsWith("IGK")) return(Locus.IGKL);
		if (gene.startsWith("IGL")) return(Locus.IGKL);
		return(null);
	}

}
