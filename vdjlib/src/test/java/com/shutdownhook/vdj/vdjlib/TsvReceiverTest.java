//
// TSVRECEIVERTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class TsvReceiverTest 
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	@BeforeClass
	public static void beforeClass() throws Exception {
		store = new Helpers.TempRepertoireStore();
	}
	
	@AfterClass
	public static void afterClass() {
		store.close();
	}

	private static Helpers.TempRepertoireStore store;

	// +-------+
	// | basic |
	// +-------+

	private static String TEST_USER = "foo@bar.com";
	private static String TEST_CONTEXT = "context";
	
	@Test
    public void basicV2() throws Exception {
		basic(SideLoadedTsv.TEST_V2_TCRB);
	}

	@Test
    public void basicV3() throws Exception {
		basic(SideLoadedTsv.TEST_V3_TCRB);
	}

    private void basic(int which) throws Exception {

		SideLoadedTsv truth = SideLoadedTsv.getTsv(which);
		
		String name = truth.getResourceName();
		Helpers.ResourceStreamReader rdr = new Helpers.ResourceStreamReader(name);

		CompletableFuture<Boolean> future =
			TsvReceiver.receive(rdr.get(), store.get(), TEST_USER, TEST_CONTEXT, name);

		Assert.assertTrue(future.get());
		truth.assertRepertoire(findRepertoireInStore(TEST_USER, TEST_CONTEXT, name));

		rdr.close();

		Assert.assertTrue(store.get().deleteRepertoire(TEST_USER, TEST_CONTEXT, name));
		Assert.assertNull(findRepertoireInStore(TEST_USER, TEST_CONTEXT, name));
	}

	private Repertoire findRepertoireInStore(String userId, String ctx, String rep) {
		Repertoire[] reps = store.get().getContextRepertoires(userId, ctx);
		for (Repertoire r : reps) if (r.Name.equals(rep)) return(r);
		return(null);
	}
}
