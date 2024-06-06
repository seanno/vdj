//
// OVERLAPTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.google.gson.GsonBuilder;
	
import com.shutdownhook.vdj.vdjlib.Overlap.OverlapByType;
import com.shutdownhook.vdj.vdjlib.Overlap.OverlapParams;
import com.shutdownhook.vdj.vdjlib.Overlap.OverlapResult;

public class OverlapTest
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private static String TEST_USER = "foo@bar.com";
	private static String TEST_CONTEXT = "context";

	@BeforeClass
	public static void beforeClass() throws Exception {

		Helpers.init();

		store = new Helpers.TempRepertoireStore();
		store.addFromResource(TEST_USER, TEST_CONTEXT, "A_BCell_ID.tsv");
		store.addFromResource(TEST_USER, TEST_CONTEXT, "A_BCell_MRD.tsv");
		store.addFromResource(TEST_USER, TEST_CONTEXT, "02583-02BH.tsv");
		crs = new ContextRepertoireStore(store.get(), TEST_USER, TEST_CONTEXT);
	}
	
	@AfterClass
	public static void afterClass() {
		store.close();
	}

	// +-------+
	// | Tests |
	// +-------+

	@Test
	public void basicAmino() throws Exception {
		OverlapResult result = basicHelper(OverlapByType.AminoAcid, 1000);
		System.out.println(String.format("----- Amino 1000 (%d)", result.Items.size()));
		Assert.assertEquals(64, result.Items.size());
		Assert.assertEquals("CQQYNSYPPPSG", result.Items.get(0).Key);
		Assert.assertEquals(2, result.Items.get(0).PresentIn);
		Assert.assertEquals(46589, result.Items.get(0).Counts[0]);
		Assert.assertEquals(293, result.Items.get(0).Counts[1]);
		Assert.assertEquals(0, result.Items.get(0).Counts[2]);
	}

	@Test
	public void basicCDR3() throws Exception {
		OverlapResult result = basicHelper(OverlapByType.CDR3, 1000);
		System.out.println(String.format("----- CDR3 1000 (%d)", result.Items.size()));
		Assert.assertEquals(98, result.Items.size());
		Assert.assertEquals("TGTCTACAACATGATAATTTCGC", result.Items.get(0).Key);
		Assert.assertEquals(2, result.Items.get(0).PresentIn);
		Assert.assertEquals(49939, result.Items.get(0).Counts[0]);
		Assert.assertEquals(285, result.Items.get(0).Counts[1]);
		Assert.assertEquals(0, result.Items.get(0).Counts[2]);
	}

	@Test
	public void basicTrunc() throws Exception {
		OverlapResult result = basicHelper(OverlapByType.CDR3, 10);
		System.out.println(String.format("----- CDR3 10 (%d)", result.Items.size()));
		Assert.assertEquals(10, result.Items.size());
		Assert.assertEquals("TGTCTACAACATGATAATTTCGC", result.Items.get(0).Key);
		Assert.assertEquals(2, result.Items.get(0).PresentIn);
		Assert.assertEquals(49939, result.Items.get(0).Counts[0]);
		Assert.assertEquals(285, result.Items.get(0).Counts[1]);
		Assert.assertEquals(0, result.Items.get(0).Counts[2]);
	}

	private OverlapResult basicHelper(OverlapByType overlapBy, int maxOverlaps) throws Exception {

		OverlapParams params = new OverlapParams();
		params.MaxOverlaps = maxOverlaps;

		String [] reps = new String[] { "A_BCell_ID.tsv", "A_BCell_MRD.tsv", "02583-02BH.tsv" };
		
		return(Overlap.overlapAsync(crs, reps, overlapBy, params).get());
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;

}
