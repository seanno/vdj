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

import com.shutdownhook.vdj.vdjlib.RearrangementKey.KeyType;
import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

import com.shutdownhook.vdj.vdjlib.RepertoireResult;
import com.shutdownhook.vdj.vdjlib.Searcher;

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
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_1));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_2));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_3));

		crs = new ContextRepertoireStore(store.get(), TEST_USER, TEST_CONTEXT);

		searcher = new Searcher(new Searcher.Config());
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
		
		Searcher.Params params = new Searcher.Params();
		params.CRS = crs;
		params.Repertoires = new String[] { REP_1, REP_2 };
		params.Motif = "AAAAAAAAAA";
		params.Extractor = RearrangementKey.getExtractor(KeyType.Rearrangement);
		params.Matcher = RearrangementKey.getMatcher(KeyType.Rearrangement, 0, false);

		RepertoireResult[] results = searcher.searchAsync(params).get();
		
		Assert.assertEquals(2, results.length);
		Assert.assertEquals(REP_1, results[0].Repertoire.Name);
		Assert.assertEquals(0, results[0].Rearrangements.size());
		Assert.assertEquals(REP_2, results[1].Repertoire.Name);
		Assert.assertEquals(0, results[1].Rearrangements.size());
	}

	@Test
	public void testNucleotideFoundTwoOfThree() throws Exception {
		
		Searcher.Params params = new Searcher.Params();
		params.CRS = crs;
		params.Repertoires = new String[] { REP_1, REP_2, REP_3 };
		params.Motif = "CAGCTCTTTACTTCT";
		params.Extractor = RearrangementKey.getExtractor(KeyType.Rearrangement);
		params.Matcher = RearrangementKey.getMatcher(KeyType.Rearrangement, 0, false);

		RepertoireResult[] results = searcher.searchAsync(params).get();

		Assert.assertEquals(128, results[0].Rearrangements.size());
		Assert.assertEquals(128, results[1].Rearrangements.size());
		Assert.assertEquals(0, results[2].Rearrangements.size());
	}

	@Test
	public void testAAFoundTwoOfTwoWithMuts() throws Exception {
		
		Searcher.Params params = new Searcher.Params();
		params.CRS = crs;
		params.Repertoires = new String[] { REP_1, REP_3 };
		params.Motif = "CARGG";
		params.Extractor = RearrangementKey.getExtractor(KeyType.AminoAcid);
		params.Matcher = RearrangementKey.getMatcher(KeyType.AminoAcid, 1, false);

		RepertoireResult[] results = searcher.searchAsync(params).get();

		Assert.assertEquals(25, results[0].Rearrangements.size());
		Assert.assertEquals(860, results[1].Rearrangements.size());
	}

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;
	private static Searcher searcher;
}
