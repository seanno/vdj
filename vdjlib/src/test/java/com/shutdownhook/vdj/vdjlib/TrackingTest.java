//
// TRACKINGTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class TrackingTest 
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private static String TEST_USER = "foo-top@bar.com";
	private static String TEST_CONTEXT = "context-top";

	private static String REP_ID = "A_BCell_ID.tsv";
	private static String REP_MRD = "A_BCell_MRD.tsv";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		store = new Helpers.TempRepertoireStore();
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_ID));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_MRD));

		crs = new ContextRepertoireStore(store.get(), TEST_USER, TEST_CONTEXT);
	}
	
	@AfterClass
	public static void afterClass() {
		store.close();
	}

	// +---------+
	// | testMrd |
	// +---------+

	@Test
	public void testMrd() throws Exception {

		Tracking tracking = new Tracking(new Tracking.Config());
		
		RepertoireResult[] dxOptions =
			tracking.getDxOptionsAsync(crs, new String[] { REP_ID, REP_MRD }).get();

		verifyDxOptions(dxOptions);

		// add one non-dx selection just for kicks
		dxOptions[1].SelectionIndices.add(0);

		Tracking.Params params = new Tracking.Params();
		params.CRS = crs;
		params.Repertoires = new String[] { REP_ID, REP_MRD };
		params.Targets = getDefaultOptions(dxOptions);

		Tracking.Results results = tracking.trackAsync(params).get();

		verifyTrackingResults(results);
	}

	private void verifyTrackingResults(Tracking.Results results) {

		Assert.assertEquals(2, results.Repertoires.length);
		Assert.assertEquals(REP_ID, results.Repertoires[0].Name);
		Assert.assertEquals(REP_MRD, results.Repertoires[1].Name);
		Assert.assertEquals(4, results.TargetValues.length);
		
		Tracking.TargetValues tv = results.TargetValues[0];
		Assert.assertEquals(0.7964449787471556, tv.Values[0], .000001);
		Assert.assertEquals(1.740349427740596E-4, tv.Values[1], .000001);
		Assert.assertEquals("TCCGTAGACACGTCCAAGAACCAGTTCTCCCTGAAGCTGAGCTCTGTGACCGCCGCAGACACGGCTGTGTATTACTGGAGGGAAATATTGTAGTAGTACCAGCTGCTATGCGGCTACTTTGACTACTGGGGCCAGGGAACC", tv.Target.Rearrangement);

		tv = results.TargetValues[3];
		Assert.assertEquals(0.0, tv.Values[0], .000001);
		Assert.assertEquals(2.658335939076295E-4, tv.Values[1], .000001);
		Assert.assertEquals("GTTTGTGTCTGGGCAGGAACAGGGACTGTGTCCCTGTGTGATGCTTTTGATATCTGGGGCCAAGGGACA", tv.Target.Rearrangement);
		
	}

	private void verifyDxOptions(RepertoireResult[] dxOptions) {

		Assert.assertEquals(2, dxOptions.length);
		
		RepertoireResult rr = dxOptions[0];
		Assert.assertEquals(3, rr.SelectionIndices.size());
		Assert.assertEquals(REP_ID, rr.Repertoire.Name);
		Assert.assertTrue(rr.Rearrangements.get(0).Dx);
		Assert.assertEquals("TCCGTAGACACGTCCAAGAACCAGTTCTCCCTGAAGCTGAGCTCTGTGACCGCCGCAGACACGGCTGTGTATTACTGGAGGGAAATATTGTAGTAGTACCAGCTGCTATGCGGCTACTTTGACTACTGGGGCCAGGGAACC", rr.Rearrangements.get(0).Rearrangement);

		rr = dxOptions[1];
		Assert.assertEquals(0, rr.SelectionIndices.size());
		Assert.assertEquals(REP_MRD, rr.Repertoire.Name);
		Assert.assertFalse(rr.Rearrangements.get(0).Dx);
		Assert.assertEquals("GTTTGTGTCTGGGCAGGAACAGGGACTGTGTCCCTGTGTGATGCTTTTGATATCTGGGGCCAAGGGACA", rr.Rearrangements.get(0).Rearrangement);
	}

	private Rearrangement[] getDefaultOptions(RepertoireResult[] dxOptions) {

		List<Rearrangement> rearrangements = new ArrayList<Rearrangement>();

		for (int i = 0; i < dxOptions.length; ++i) {
			
			RepertoireResult rr = dxOptions[i];
			
			for (int j = 0; j < rr.SelectionIndices.size(); ++j) {
				rearrangements.add(rr.Rearrangements.get(rr.SelectionIndices.get(j)));
			}
		}

		return(rearrangements.toArray(new Rearrangement[rearrangements.size()]));
	}

	// +----------+
	// | mrdMatch |
	// +----------+

	@Test
	public void testMrdMatch() throws Exception {
		Assert.assertTrue("full", matchTwo("aaaaa", 2, "aaaaa", 2, 3));
		Assert.assertTrue("partial1", matchTwo("abcde", 1, "bcd", 0, 3));
		Assert.assertTrue("partial2", matchTwo("abcde", -1, "cde", -1, 3));
		Assert.assertTrue("partial3", matchTwo("abcde", 0, "aabc", 1, 3));
		Assert.assertFalse("fail1", matchTwo("abcde", 2, "fghijklmn", 2, 4));
		Assert.assertFalse("fail-length1", matchTwo("abcde", 1, "bcd", 0, 4));
		Assert.assertFalse("fail-length2", matchTwo("abcde", -1, "cde", -1, 4));
	}

	private static boolean matchTwo(String seq1, int jIndex1,
									String seq2, int jIndex2,
									int cchMatchMin)
	{
		Rearrangement r1 = makeMrdRearrangement(seq1, jIndex1);
		Rearrangement r2 = makeMrdRearrangement(seq2, jIndex2);
		return(Tracking.mrdMatch(r1, r2, cchMatchMin));
	}
									
	private static Rearrangement makeMrdRearrangement(String seq, int jIndex) {

		Rearrangement r = new Rearrangement();
		r.Rearrangement = seq;
		r.JIndex = jIndex;
		return(r);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;

}
