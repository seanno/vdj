//
// GENEUSETEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.shutdownhook.vdj.vdjlib.GeneUse.VJPair;

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
		
		GeneUse.Params params = new GeneUse.Params();
		params.CRS = crs;
		params.Repertoire = TEST_REPERTOIRE;

		GeneUse geneUse = new GeneUse();
		VJPair[] actual = geneUse.getAsync(params).get();

		VJPair[] expected = loadTruth(TEST_REPERTOIRE);
		assertEqual(actual, expected);
	}

	private VJPair[] loadTruth(String rep) throws Exception {

		InputStream resInputStream = this.getClass()
			.getClassLoader().getResourceAsStream(rep);
		
		InputStreamReader resStreamReader = new InputStreamReader(resInputStream);
		BufferedReader resBufferedReader = new BufferedReader(resStreamReader);

		String line = null;

		Map<String,VJPair> countMap = new HashMap<String,VJPair>();
		
		while ((line = resBufferedReader.readLine()) != null) {

			if (line.startsWith("#")) continue;
			if (line.startsWith("nucleotide")) continue;
			
			String[] fields = line.split("\t");

			String v = normalizeGene(fields[10], fields[9]);
			if (v == null) continue;
			
			String j = normalizeGene(fields[22], fields[21]);
			if (j == null) continue;

			long count = Long.parseLong(fields[40]);

			String key = v + "|" + j;
			VJPair pair = countMap.get(key);
			
			if (pair == null) {
				pair = new VJPair(v, j, count);
				countMap.put(key, pair);
			}
			else {
				pair.accumulate(count);
			}
		}
		
		resBufferedReader.close();
		resStreamReader.close();
		resInputStream.close();

		VJPair[] result = countMap.values().toArray(new VJPair[countMap.size()]);
		Arrays.sort(result);
			
		return(result);
	}

	private String normalizeGene(String gene, String family) {
		if (!gene.isEmpty()) return(gene);
		if (family.isEmpty()) return("X");
		return(family + "-X");
	}

	private void assertEqual(VJPair[] actual, VJPair[] expected) throws Exception {

		Assert.assertEquals(expected.length, actual.length);

		for (int i = 0; i < expected.length; ++i) {
			Assert.assertEquals(expected[i].V, actual[i].V);
			Assert.assertEquals(expected[i].J, actual[i].J);
			Assert.assertEquals(expected[i].Count, actual[i].Count);
			Assert.assertEquals(expected[i].Uniques, actual[i].Uniques);
		}
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;

}
