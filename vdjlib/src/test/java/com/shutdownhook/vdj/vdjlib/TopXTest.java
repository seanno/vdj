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
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		store = new Helpers.TempRepertoireStore();
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_1));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_2));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_3));

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
		params.Repertoire = REP_1;
		params.Sort = TopXSort.Count;
		params.Count = 100;

		RepertoireResult result = topx.getAsync(params).get();

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
		params.Repertoire = REP_2;
		params.Sort = TopXSort.FractionOfCells;
		params.Count = 10;

		RepertoireResult result = topx.getAsync(params).get();

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

	// +---------+
	// | Helpers |
	// +---------+

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;
	private static TopXRearrangements topx;

}
