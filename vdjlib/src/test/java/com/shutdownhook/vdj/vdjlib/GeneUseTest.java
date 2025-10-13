//
// GENEUSETEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

public class GeneUseTest
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private static String TEST_USER = "foo-geneuse@bar.com";
	private static String TEST_CONTEXT = "context-geneuse";

	private static String TEST_REPERTOIRE = "A_BCell_ID.tsv";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		store = new Helpers.TempRepertoireStore();
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, TEST_REPERTOIRE));
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
	public void basic() throws Exception {
		testOne(true, true);
		testOne(true, false);
		testOne(false, true);
		testOne(false, false);
	}

	private void testOne(Boolean includeUnknown,
						 Boolean includeFamilyOnly) throws Exception {
		
		GeneUse.Params params = new GeneUse.Params();
		params.CRS = crs;
		params.Repertoire = TEST_REPERTOIRE;
		params.IncludeUnknown = includeUnknown;
		params.IncludeFamilyOnly = includeFamilyOnly;

		GeneUse geneUse = new GeneUse();
		GeneUse.Result actual = geneUse.getAsync(params).get();

		GeneUse.Result expected = loadTruth(params);
		assertEqual(actual, expected);
	}

	private GeneUse.Result loadTruth(GeneUse.Params params) throws Exception {

		InputStream resInputStream = this.getClass()
			.getClassLoader().getResourceAsStream(TEST_REPERTOIRE);
		
		InputStreamReader resStreamReader = new InputStreamReader(resInputStream);
		BufferedReader resBufferedReader = new BufferedReader(resStreamReader);

		String line = null;

		Map<String,Long> countMap = new HashMap<String,Long>();
		
		while ((line = resBufferedReader.readLine()) != null) {

			if (line.startsWith("#")) continue;
			if (line.startsWith("nucleotide")) continue;
			
			String[] fields = line.split("\t");

			String v = normalizeGene(fields[10], fields[9], params);
			if (v == null) continue;
			
			String j = normalizeGene(fields[22], fields[21], params);
			if (j == null) continue;

			long count = Long.parseLong(fields[40]);

			String key = v + "|" + j;
			Long prevCount = countMap.get(key);
			if (prevCount == null) prevCount = 0L;
			countMap.put(key, prevCount + count);
		}
		
		resBufferedReader.close();
		resStreamReader.close();
		resInputStream.close();

		String[] keys = countMap.keySet().toArray(new String[countMap.size()]);
		Arrays.sort(keys);
			
		GeneUse.Result result = new GeneUse.Result();
		result.VGenes = new String[keys.length];
		result.JGenes = new String[keys.length];
		result.Counts = new long[keys.length];
		
		for (int i = 0; i < keys.length; ++i) {
			String[] split = keys[i].split("\\|");
			result.VGenes[i] = split[0];
			result.JGenes[i] = split[1];
			result.Counts[i] = countMap.get(keys[i]);
		}

		return(result);
	}

	private String normalizeGene(String gene, String family, GeneUse.Params params) {
		if (!gene.isEmpty()) return(gene);
		if (family.isEmpty()) return(params.IncludeUnknown ? "X" : null);
		return(params.IncludeFamilyOnly ? family + "-X" : null);
	}

	private void assertEqual(GeneUse.Result actual,
							 GeneUse.Result expected) throws Exception {

		Assert.assertEquals(expected.VGenes.length, actual.VGenes.length);
		Assert.assertEquals(expected.JGenes.length, actual.JGenes.length);
		Assert.assertEquals(expected.Counts.length, actual.Counts.length);

		Assert.assertEquals(actual.VGenes.length, actual.JGenes.length);
		Assert.assertEquals(actual.VGenes.length, actual.Counts.length);

		for (int i = 0; i < expected.VGenes.length; ++i) {
			Assert.assertEquals(expected.VGenes[i], actual.VGenes[i]);
			Assert.assertEquals(expected.JGenes[i], actual.JGenes[i]);
			Assert.assertEquals(expected.Counts[i], actual.Counts[i]);
		}
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;

}
