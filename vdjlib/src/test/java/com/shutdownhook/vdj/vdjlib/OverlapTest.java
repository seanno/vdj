//
// OVERLAPTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.google.gson.Gson;
	
import com.shutdownhook.vdj.vdjlib.RearrangementKey;
import com.shutdownhook.vdj.vdjlib.RearrangementKey.KeyType;
import com.shutdownhook.vdj.vdjlib.Overlap.OverlapResult;
import com.shutdownhook.vdj.vdjlib.Overlap.OverlapResultItem;

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
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, "A_BCell_ID.tsv"));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, "A_BCell_MRD.tsv"));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, "BH.tsv"));
		crs = new ContextRepertoireStore(store.get(), TEST_USER, TEST_CONTEXT);
	}
	
	@AfterClass
	public static void afterClass() {
		store.close();
	}

	// +------------------+
	// | basic / standard |
	// +------------------+

	@Test
	public void basicAmino() throws Exception {
		OverlapResult result = basicHelper(KeyType.AminoAcid, 1000);
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
		OverlapResult result = basicHelper(KeyType.CDR3, 1000);
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
		OverlapResult result = basicHelper(KeyType.CDR3, 10);
		System.out.println(String.format("----- CDR3 10 (%d)", result.Items.size()));
		Assert.assertEquals(10, result.Items.size());
		Assert.assertEquals("TGTCTACAACATGATAATTTCGC", result.Items.get(0).Key);
		Assert.assertEquals(2, result.Items.get(0).PresentIn);
		Assert.assertEquals(49939, result.Items.get(0).Counts[0]);
		Assert.assertEquals(285, result.Items.get(0).Counts[1]);
		Assert.assertEquals(0, result.Items.get(0).Counts[2]);
	}

	private OverlapResult basicHelper(KeyType keyType, int maxOverlaps) throws Exception {

		Overlap.Config cfg = new Overlap.Config();
		cfg.MaxStandardOverlaps = maxOverlaps;
		Overlap overlap = new Overlap(cfg);

		Overlap.Params params = new Overlap.Params();
		params.CRS = crs;
		params.RepertoireNames = new String[] { "A_BCell_ID.tsv", "A_BCell_MRD.tsv", "BH.tsv" };
		params.Extractor = RearrangementKey.getExtractor(keyType);
		
		return(overlap.overlapAsync(params).get());
	}
	
	// +----------+
	// | combined |
	// +----------+

	@Test
	public void combined() throws Exception {
		OverlapResult result = combinedHelper(KeyType.AminoAcid);
		System.out.println(String.format("----- Amino Combined (%d)", result.Items.size()));
		
		// Assert.assertEquals(64, result.Items.size());

		OverlapResultItem ori = findORIByKey(result, "CQQYNSYPPPSG");
		Assert.assertEquals(ori.PresentIn, 2);
		Assert.assertEquals(ori.Counts[0], 46589);
		Assert.assertEquals(ori.Counts[1], 293);

		ori = findORIByKey(result, "CARAAIAVAGFDYW, CARMGREVAQGSFFDYW, CLQHNSYPLTF, CQQRSNWPLLTF, CQQRSNWPRSPSG, CQQYDNLLFTF, CQQYDNPASG, CQQYYSTPFTF, RLMLP*PAFLLSPSG, RLMLP*PAFLVSPSG, WREIL**YQLLCGYFDPW, WRPILS*YQLLCGYFDYW, XQSYDSSLSGSVF");
		Assert.assertEquals(ori.PresentIn, 1);
		Assert.assertEquals(ori.Counts[0], 2);
		Assert.assertEquals(ori.Counts[1], 0);

		ori = findORIByKey(result, "*MPGVNLFTF, *MQGIHLLTF, *MQGIHLPDTF, *MQGIHLPRSPSG, *MQGIHLPYTF, *MQGIHPPFTF, *MQGIHPWTF, *MQGIPHTF, *TKARQ*PLGPCSGGGCYFDYW, *TKVRQ*PLGSYSGGGCYFDYW, *WGHYWSFDYW, CAADAPLPPSQIDYW, CAADKVSYYDSSG**WFDPW, CAADPHPATEALDYW, CAAGIADDYW, CAAGMEFWSGYFVLYGMDVW...");
		Assert.assertEquals(ori.PresentIn, 1);
		Assert.assertEquals(ori.Counts[0], 0);
		Assert.assertEquals(ori.Counts[1], 1);
	}

	private OverlapResultItem findORIByKey(OverlapResult result, String key) {
		
		for (OverlapResultItem ori : result.Items) {
			if (ori.Key.equals(key)) return(ori);
		}

		return(null);
	}

	private OverlapResult combinedHelper(KeyType keyType) throws Exception {

		Overlap.Config cfg = new Overlap.Config();
		cfg.MaxCombinedKeyLength = 256;
		Overlap overlap = new Overlap(cfg);

		Overlap.Params params = new Overlap.Params();
		params.CRS = crs;
		params.RepertoireNames = new String[] { "A_BCell_ID.tsv", "A_BCell_MRD.tsv" };
		params.Extractor = RearrangementKey.getExtractor(keyType);
		params.Mode = Overlap.OverlapMode.Combined;
		
		return(overlap.overlapAsync(params).get());
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;
}
