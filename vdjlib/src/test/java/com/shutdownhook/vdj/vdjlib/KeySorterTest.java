//
// KEYSORTERTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.shutdownhook.vdj.vdjlib.RearrangementKey.Extractor;
import com.shutdownhook.vdj.vdjlib.RearrangementKey.KeyType;
import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

import com.shutdownhook.vdj.vdjlib.KeySorter;
import com.shutdownhook.vdj.vdjlib.KeySorter.KeyItem;

public class KeySorterTest
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private static String TEST_USER = "foo@bar.com";
	private static String TEST_CONTEXT = "context";

	public static class TestRepertoireInfo
	{
		public String Name;
		public int Uniques;
		public Extractor Extractor;
	}

	private static TestRepertoireInfo[] TEST_INFOS;

	@BeforeClass
	public static void beforeClass() throws Exception {

		Helpers.init();

		store = new Helpers.TempRepertoireStore();
		store.addFromResource(TEST_USER, TEST_CONTEXT, "subject9-v2.tsv");
		crs = new ContextRepertoireStore(store.get(), TEST_USER, TEST_CONTEXT);

		TEST_INFOS = new TestRepertoireInfo[2];

		TEST_INFOS[0] = new TestRepertoireInfo();
		TEST_INFOS[0].Name = "subject9-v2.tsv";
		TEST_INFOS[0].Extractor = RearrangementKey.getExtractor(KeyType.AminoAcid);
		TEST_INFOS[0].Uniques = 5692;

		TEST_INFOS[1] = new TestRepertoireInfo();
		TEST_INFOS[1].Name = "subject9-v2.tsv";
		TEST_INFOS[1].Extractor = RearrangementKey.getExtractor(KeyType.CDR3);
		TEST_INFOS[1].Uniques = 5692;
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
		for (TestRepertoireInfo info : TEST_INFOS) {
			basicTestRepertoireInfo(info, true);
			basicTestRepertoireInfo(info, false);
		}
	}

	private void basicTestRepertoireInfo(TestRepertoireInfo info, boolean useCache) throws Exception {
		KeySorter.Config cfg = new KeySorter.Config();
		cfg.UseCache = useCache;
		cfg.InitialChunkSize = info.Uniques + 1; basicTestHelper(info, cfg);
		cfg.InitialChunkSize = info.Uniques / 2; basicTestHelper(info, cfg);
		cfg.InitialChunkSize = (int) ((double)info.Uniques / 3.5); basicTestHelper(info, cfg);
	}
	
	private void basicTestHelper(TestRepertoireInfo info, KeySorter.Config cfg) throws Exception {
		
		System.out.println(String.format("basicTestHelper: %s, %d", info.Name, cfg.InitialChunkSize));

		KeySorter ks = new KeySorter(crs, info.Name, info.Extractor, cfg);
		boolean sorted = ks.sortAsync().get();
		Assert.assertTrue(sorted);

		assertKeyItems(ks, getSideLoadedTruth(info.Name, info.Extractor));
		ks.close();
	}
	
	// +----------------+
	// | assertKeyItems |
	// +----------------+

	private void assertKeyItems(KeySorter ks, List<KeyItem> truth) throws Exception {

		KeyItem testItem;
		int i = 0;
		
		while ((testItem = ks.readNext()) != null) {
			Assert.assertEquals(truth.get(i).getKey(), testItem.getKey());
			Assert.assertEquals(truth.get(i).getCount(), testItem.getCount());
			++i;
		}
	}

	// +--------------------+
	// | getSideLoadedTruth |
	// +--------------------+

	// don't send me anything very big!!!!

	private static Map<String,List<KeyItem>> sideLoadedTruths = new HashMap<String,List<KeyItem>>();
	
	private static List<KeyItem> getSideLoadedTruth(String name, Extractor extractor) throws Exception {

		// this is bogus but will work for our testing purposes
		String k = name + Integer.toString(System.identityHashCode(extractor));

		if (!sideLoadedTruths.containsKey(k)) {
			sideLoadedTruths.put(k, sideLoadKeySort(name, extractor));
		}

		return(sideLoadedTruths.get(k));
	}
		
	private static List<KeyItem> sideLoadKeySort(String name, Extractor extractor) throws Exception {

		InputStream stm = crs.getRepertoireStream(name);
		InputStreamReader rdr = new InputStreamReader(stm);
		TsvReader tsv = new TsvReader(rdr, 0);

		Rearrangement r;
		Map<String,Long> map = new HashMap<String,Long>();

		while ((r = tsv.readNext()) != null) {
			String key = extractor.extract(r);
			if (Utility.nullOrEmpty(key)) continue;
			long count = (map.containsKey(key) ? map.get(key) + r.Count : r.Count);
			map.put(key, count);
		}

		List<KeyItem> items = new ArrayList<KeyItem>();
		for (String key : map.keySet()) items.add(new KeyItem(key, map.get(key)));
		Collections.sort(items);
		
		tsv.close();
		rdr.close();
		stm.close();

		return(items);
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;

}
