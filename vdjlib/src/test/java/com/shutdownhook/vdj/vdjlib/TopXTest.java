//
// TOPXTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

import com.shutdownhook.vdj.vdjlib.RepertoireResult;
import com.shutdownhook.vdj.vdjlib.TopXRearrangements.TopXSort;

public class TopXTest 
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private static String TEST_USER = "foo-top@bar.com";
	private static String TEST_CONTEXT = "context-top";

	private static String REP_1 = "subject9-v2.tsv";
	private static String REP_2 = "subject9-v3.tsv";
	private static String REP_3 = "02583-02BH.tsv";
	private static String REP_DX_1 = "A_BCell_ID.tsv";
	private static String REP_DX_2 = "D_BCell_Cellfree_MRD.tsv";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		store = new Helpers.TempRepertoireStore();
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_1));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_2));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_3));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_DX_1));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_DX_2));

		crs = new ContextRepertoireStore(store.get(), TEST_USER, TEST_CONTEXT);
		topx = new TopXRearrangements(new TopXRearrangements.Config());
	}
	
	@AfterClass
	public static void afterClass() {
		store.close();
	}

	// +-------+
	// | Tests |
	// +-------+

	@Test
	public void testByCount() throws Exception {

		TopXRearrangements.Params params = new TopXRearrangements.Params();
		params.CRS = crs;
		params.Repertoires = new String[] { REP_1 };
		params.Sort = TopXSort.Count;
		params.Count = 100;

		RepertoireResult result = topx.getAsync(params).get()[0];

		Assert.assertEquals(100, result.Rearrangements.size());
		
		Assert.assertEquals("CGCACAGAGCAGGGGGACTCGGCCATGTATCTCTGTGCCAGCAGCTTACCAAGCGCAAACCCCACCGGGGAGCTGTTTTTTGGAGAA",
							result.Rearrangements.get(0).Rearrangement);

		Assert.assertEquals("TCTGCCAGGCCCTCACATACCTCTCAGTACCTCTGTGCCAGCAGTGAATCCGACAGGGGGGTCCACAATGAGCAGTTCTTCGGGCCA",
							result.Rearrangements.get(result.Rearrangements.size()-1).Rearrangement);
	}

	@Test
	public void testByCells() throws Exception {

		TopXRearrangements.Params params = new TopXRearrangements.Params();
		params.CRS = crs;
		params.Repertoires = new String[] { REP_2 };
		params.Sort = TopXSort.FractionOfCells;
		params.Count = 10;

		RepertoireResult result = topx.getAsync(params).get()[0];

		Assert.assertEquals(10, result.Rearrangements.size());
		
		Assert.assertEquals("CGCACAGAGCAGGGGGACTCGGCCATGTATCTCTGTGCCAGCAGCTTACCAAGCGCAAACCCCACCGGGGAGCTGTTTTTTGGAGAA",
							result.Rearrangements.get(0).Rearrangement);

		Assert.assertEquals("CGGCTGCTCCCTCCCAGACATCTGTGTACTTCTGTGCCAGCAGTTACGGCCGAGGACTAGCGCGAAGAGACCCAGTACTTCGGGCCA",
							result.Rearrangements.get(1).Rearrangement);

		Assert.assertEquals("TCGCCCAGCCCCAACCAGACCTCTCTGTACTTCTGTGCCAGCAGTCAAACGGGACTAGCGATCTACAATGAGCAGTTCTTCGGGCCA",
							result.Rearrangements.get(8).Rearrangement);

		Assert.assertEquals("NNNNTGTCGGCTGCTCCCTCCCAGACATCTGTGTACTTCTGTGCCGGCCAAAGGGCAACAGGTTCCTACGAGCAGTACTTCGGGCCG",
							result.Rearrangements.get(9).Rearrangement);
	}

	// +-------------+
	// | DxPotential |
	// +-------------+

	@Test
	public void testDxPotential() throws Exception {

		TopXRearrangements.Params params = new TopXRearrangements.Params();
		params.CRS = crs;
		params.Repertoires = new String[] { REP_DX_1, REP_DX_2 };
		params.Sort = TopXSort.DxPotential;
		params.Count = 10;

		RepertoireResult[] results = topx.getAsync(params).get();

		assertDxRearrangement(results[0], 0, true, 0.985811, "TCCGTAGACACGTCCAAGAACCAGTTCTCCCTGAAGCTGAGCTCTGTGACCGCCGCAGACACGGCTGTGTATTACTGGAGGGAAATATTGTAGTAGTACCAGCTGCTATGCGGCTACTTTGACTACTGGGGCCAGGGAACC");

		assertDxRearrangement(results[0], 9, false, 0.000080, "CTGCGGACACGGCTGTGTATTACTGGAGGGAAATATTGTAGTAGTACCAGCTGCTATGCGGCTACTTTGACTACTGGGGCCAGGGAACC");

		assertDxRearrangement(results[1], 0, true, 0.981481, "GATATTGGAGTTTATTATTGCATGCAACGTATAGAGTTTCCGTACACTTTTGGCCAGGGG");
		
		assertDxRearrangement(results[1], 9, false, 0.001425, "CCTGAAGATTTTGCAACTTATTACTGTCTACAAGATTACAGGCTCTTGAACGTGCGGTATTTGGCAGCCCAGGG");
	}

	private void assertDxRearrangement(RepertoireResult result, int i, boolean dxExpected,
									   double fractionExpected, String rearrangementExpected) {

		Rearrangement r = result.Rearrangements.get(i);
		Assert.assertEquals(dxExpected, r.Dx);
		Assert.assertEquals(fractionExpected, r.getFractionOfLocus(result.Repertoire), 0.000001);
		Assert.assertEquals(rearrangementExpected, r.Rearrangement);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;
	private static TopXRearrangements topx;

}
