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

	public static int[][] COLS = {
		// in assertRearrangement order
		{ 0, 1, 38, 2, 3, 5, 12, 19, 4, 32, 34, 36, 33, 35, 49 },
		{ 52, 53, 54, 56, 58, 96, 97, 98, 60, 76, 79, 80, 77, 78, 92 }
	};

	public static int[][] CELL_COLS = {
		{ -1, -1 },
		{ 37, 35 }
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

	public static SideLoadedTsv getTsv(int which) throws Exception {
		ensureTestTsvs();
		return(tsvs[which]);
	}
	
	private static void ensureTestTsvs() throws Exception {
		
		if (tsvs != null) return;

		tsvs = new SideLoadedTsv[3];
		tsvs[TEST_V2_TCRB] = new SideLoadedTsv("subject9-v2.tsv", V2);
		tsvs[TEST_V3_TCRB] = new SideLoadedTsv("subject9-v3.tsv", V3);
		tsvs[TEST_V2_IGH] = new SideLoadedTsv("02583-02BH.tsv", V2);
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

		Assert.assertEquals(truth[cols[5]], r.VResolved);
		Assert.assertEquals(truth[cols[6]], r.DResolved);
		Assert.assertEquals(truth[cols[7]], r.JResolved);
		Assert.assertEquals(truth[cols[8]], Integer.toString(r.Cdr3Length));
		Assert.assertEquals(truth[cols[9]], Integer.toString(r.VIndex));
		Assert.assertEquals(truth[cols[10]], Integer.toString(r.DIndex));
		Assert.assertEquals(truth[cols[11]], Integer.toString(r.JIndex));
		Assert.assertEquals(truth[cols[12]], Integer.toString(r.N1Index));
		Assert.assertEquals(truth[cols[13]], Integer.toString(r.N2Index));

		Assert.assertEquals(locusFromGene(r.VResolved, r.DResolved, r.JResolved), r.Locus);
		
		int[] vSHMIndices = r.VSHMIndices;
		if (vSHMIndices != null) {
			String[] csv = truth[cols[14]].trim().split(",");
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
				
				String v = fields[COLS[ver][5]];
				String d = fields[COLS[ver][6]];
				String j = fields[COLS[ver][7]];
				long c = Long.parseLong(fields[COLS[ver][3]]);
				repertoire.accumulateCount(locusFromGene(v,d,j), c);
			}
		}
		
		resBufferedReader.close();
		resStreamReader.close();
		resInputStream.close();
	}

	// +---------+
	// | Helpers |
	// +---------+
	
	private static Locus locusFromGene(String v, String d, String j) {

		String gene = j;
		if (gene.isEmpty()) gene = d;
		if (gene.isEmpty()) gene = v;
		
		if (gene.startsWith("TCRB")) return(Locus.TCRB);
		if (gene.startsWith("TCRG")) return(Locus.TCRG);
		if (gene.startsWith("TCRAD")) return(Locus.TCRAD);
		if (gene.startsWith("IGH")) return(Locus.IGH);
		if (gene.startsWith("IGK")) return(Locus.IGKL);
		if (gene.startsWith("IGL")) return(Locus.IGKL);
		return(null);
	}

}
