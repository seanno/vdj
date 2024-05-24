//
// SEARCHERTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

import com.shutdownhook.vdj.vdjlib.Searcher.SearchParams;
import com.shutdownhook.vdj.vdjlib.Searcher.RepertoireResult;

public class SearcherTest 
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private static String TEST_USER = "foo@bar.com";
	private static String TEST_CONTEXT = "context";

	private static String REP_1 = "subject9-v2.tsv";
	private static String REP_2 = "subject9-v3.tsv";
	private static String REP_3 = "02583-02BH.tsv";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		store = new Helpers.TempRepertoireStore();
		store.addFromResource(TEST_USER, TEST_CONTEXT, REP_1);
		store.addFromResource(TEST_USER, TEST_CONTEXT, REP_2);
		store.addFromResource(TEST_USER, TEST_CONTEXT, REP_3);
	}
	
	@AfterClass
	public static void afterClass() {
		store.close();
	}

	// +-------+
	// | Tests |
	// +-------+

	@Test
	public void testNucleotideNotFound() throws Exception {
		
		SearchParams params = initParams();
		params.Repertoires = new String[] { REP_1, REP_2 };
		params.Motif = "AAAAAAAAAA";
		params.AllowedMutations = 0;

		RepertoireResult[] results = Searcher.searchAsync(params).get();
		
		Assert.assertEquals(2, results.length);
		Assert.assertEquals(REP_1, results[0].Repertoire.Name);
		Assert.assertEquals(0, results[0].Rearrangements.size());
		Assert.assertEquals(REP_2, results[1].Repertoire.Name);
		Assert.assertEquals(0, results[1].Rearrangements.size());
	}

	@Test
	public void testNucleotideFoundTwoOfThree() throws Exception {
		
		SearchParams params = initParams();
		params.Repertoires = new String[] { REP_1, REP_2, REP_3 };
		params.Motif = "CAGCTCTTTACTTCT";
		params.AllowedMutations = 0;

		RepertoireResult[] results = Searcher.searchAsync(params).get();

		Assert.assertEquals(128, results[0].Rearrangements.size());
		Assert.assertEquals(128, results[1].Rearrangements.size());
		Assert.assertEquals(0, results[2].Rearrangements.size());
	}

	@Test
	public void testAAFoundTwoOfTwoWithMuts() throws Exception {
		
		SearchParams params = initParams();
		params.Repertoires = new String[] { REP_1, REP_3 };
		params.Motif = "CARGG";
		params.MotifIsAA = true;
		params.AllowedMutations = 1;

		RepertoireResult[] results = Searcher.searchAsync(params).get();

		Assert.assertEquals(25, results[0].Rearrangements.size());
		Assert.assertEquals(860, results[1].Rearrangements.size());
	}

	// +----------+
	// | Matching |
	// +----------+

	@Test
	public void testMatches() throws Exception {
		Assert.assertTrue(Searcher.matches("ABCDEFG", "ABCD", 0));
		Assert.assertTrue(Searcher.matches("ABCDEFG", "BCD", 0));
		Assert.assertTrue(Searcher.matches("ABCDEFG", "AXXDE", 2));
		Assert.assertTrue(Searcher.matches("ABCDEFG", "GGD", 2));
		
		Assert.assertFalse(Searcher.matches("ABCDEFG", "AXXE", 1));
		Assert.assertFalse(Searcher.matches("ABCDEFG", "AXXE", 0));
		Assert.assertFalse(Searcher.matches("ABCDEFG", "EFGH", 0));

		Assert.assertTrue(Searcher.matches(
			 "GCCATGGGTATGGTGGCTACGCCCCGGGACCCTACGGTATGGACGTCTGGGGCCAAGGG",
			 "GCCATGGGTATGGTGGCTACGCCCCGGGACCCTACGGTATGGACGTCTGGGGCCAAGGG",
			 0));
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private SearchParams initParams() {
		SearchParams params = new SearchParams();
		params.Store = store.get();
		params.UserId = TEST_USER;
		params.Context = TEST_CONTEXT;
		return(params);
	}
	
	private static Helpers.TempRepertoireStore store;

}
