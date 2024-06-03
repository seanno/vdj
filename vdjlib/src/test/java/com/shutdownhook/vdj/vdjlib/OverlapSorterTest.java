//
// OVERLAPSORTERTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileReader;
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

import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

import com.shutdownhook.vdj.vdjlib.OverlapSorter.OverlapByType;
import com.shutdownhook.vdj.vdjlib.OverlapSorter.OverlapItem;
import com.shutdownhook.vdj.vdjlib.OverlapSorter.OverlapSorterParams;

public class OverlapSorterTest
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class TestRepertoireInfo
	{
		public String Name;
		public int Uniques;
		public List<OverlapItem> Truth_AminoAcid;
		public List<OverlapItem> Truth_CDR3;
	}

	private static TestRepertoireInfo TEST_INFO;

	private static String TEST_USER = "foo@bar.com";
	private static String TEST_CONTEXT = "context";

	@BeforeClass
	public static void beforeClass() throws Exception {

		Helpers.init();

		store = new Helpers.TempRepertoireStore();
		store.addFromResource(TEST_USER, TEST_CONTEXT, "subject9-v2.tsv");

		TEST_INFO = new TestRepertoireInfo();
		TEST_INFO.Name = "subject9-v2.tsv";
		TEST_INFO.Uniques = 5692;
		TEST_INFO.Truth_AminoAcid = sideLoadOverlapSort(TEST_INFO.Name, OverlapByType.AminoAcid);
		TEST_INFO.Truth_CDR3 = sideLoadOverlapSort(TEST_INFO.Name, OverlapByType.CDR3);
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
		basicTestRepertoireInfo(TEST_INFO);
	}

	private void basicTestRepertoireInfo(TestRepertoireInfo info) throws Exception {
		
		OverlapSorterParams params = initParams(info.Name);

		params.SortBy = OverlapByType.AminoAcid;
		params.InitialChunkSize = info.Uniques + 1; basicTestParams(params, info.Truth_AminoAcid);
		params.InitialChunkSize = info.Uniques / 2; basicTestParams(params, info.Truth_AminoAcid);
		params.InitialChunkSize = (int) ((double)info.Uniques / 3.5); basicTestParams(params, info.Truth_AminoAcid);

		params.SortBy = OverlapByType.CDR3;
		params.InitialChunkSize = info.Uniques + 1; basicTestParams(params, info.Truth_CDR3);
		params.InitialChunkSize = info.Uniques / 2; basicTestParams(params, info.Truth_CDR3);
		params.InitialChunkSize = (int) ((double)info.Uniques / 3.5); basicTestParams(params, info.Truth_CDR3);
	}
	
	private void basicTestParams(OverlapSorterParams params, List<OverlapItem> truth) throws Exception {
		
		System.out.println(String.format("basicTestParams: %s, %s, %d",
							 params.Repertoire, params.SortBy, params.InitialChunkSize));
		
		OverlapSorter os = new OverlapSorter(params);
		File sorted = os.sortAsync().get();
		assertOverlaps(sorted, truth);
		sorted.delete();
		os.close();
	}
	
	// +----------------+
	// | assertOverlaps |
	// +----------------+

	private void assertOverlaps(File sorted, List<OverlapItem> truth) throws Exception {

		FileReader rdr = new FileReader(sorted);
		BufferedReader buf = new BufferedReader(rdr);

		String line;

		int i = 0;
		while ((line = buf.readLine()) != null) {
			OverlapItem testItem = OverlapItem.fromString(line);
			Assert.assertEquals(truth.get(i).getKey(), testItem.getKey());
			Assert.assertEquals(truth.get(i).getCount(), testItem.getCount());
			++i;
		}
		
		buf.close();
		rdr.close();
	}

	// +---------------------+
	// | sideLoadOverlapSort |
	// +---------------------+

	// don't send me anything very big!!!!
	
	private static List<OverlapItem> sideLoadOverlapSort(String name, OverlapByType sortBy) 
		throws Exception {

		InputStream stm = store.get().getRepertoireStream(TEST_USER, TEST_CONTEXT, name);
		InputStreamReader rdr = new InputStreamReader(stm);
		TsvReader tsv = new TsvReader(rdr, 0);

		Rearrangement r;
		Map<String,Long> map = new HashMap<String,Long>();

		while ((r = tsv.readNext()) != null) {
			String key = (sortBy.equals(OverlapByType.AminoAcid) ? r.AminoAcid : r.getCDR3());
			if (Utility.nullOrEmpty(key)) continue;
			long count = (map.containsKey(key) ? map.get(key) + r.Count : r.Count);
			map.put(key, count);
		}

		List<OverlapItem> overlaps = new ArrayList<OverlapItem>();
		for (String key : map.keySet()) overlaps.add(new OverlapItem(key, map.get(key)));
		Collections.sort(overlaps);
		
		tsv.close();
		rdr.close();
		stm.close();

		return(overlaps);
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private OverlapSorterParams initParams(String rep) {
		OverlapSorterParams params = new OverlapSorterParams();
		params.Store = store.get();
		params.UserId = TEST_USER;
		params.Context = TEST_CONTEXT;
		params.Repertoire = rep;
		return(params);
	}
	
	private static Helpers.TempRepertoireStore store;

}
