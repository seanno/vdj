//
// ADMINOPSTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.shutdownhook.vdj.vdjlib.model.Repertoire;
import com.shutdownhook.vdj.vdjlib.TsvReceiver.ReceiveResult;

public class AdminOpsTest
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private static String TEST_USER = "foo-admin@bar.com";
	private static String TEST_CONTEXT = "context-admin";
	private static String TEST_REP = "subject9-v2.tsv";
	private static String TEST_REP_MOVE = "subject9-v3.tsv";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		store = new Helpers.TempRepertoireStore();
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, TEST_REP));
		sourceRep = store.findRepertoire(new RepertoireSpec(TEST_USER, TEST_CONTEXT, TEST_REP));
	}
	
	@AfterClass
	public static void afterClass() {
		store.close();
	}
	// +------+
	// | move |
	// +------+

	@Test
	public void moveOK() throws Exception {

		RepertoireSpec specFrom = new RepertoireSpec(TEST_USER, TEST_CONTEXT, TEST_REP_MOVE);
		RepertoireSpec specTo = new RepertoireSpec(TEST_USER, TEST_CONTEXT, "moved");
		AdminOps.MoveCopyParams params = new AdminOps.MoveCopyParams(specFrom, specTo);
		
		store.addFromResource(specFrom);
		Repertoire repFrom = store.findRepertoire(specFrom);

		boolean success = AdminOps.moveRepertoireAsync(store.get(), params).get();
		Assert.assertTrue(success);

		Repertoire repTo = store.findRepertoire(specTo);
		Helpers.assertRepertoire(repFrom, repTo, true);

		repFrom = store.findRepertoire(specFrom);
		Assert.assertNull(repFrom);
	}

	// +------+
	// | copy |
	// +------+

	@Test
	public void copyDoesntExist() throws Exception {
		
		RepertoireSpec specFrom = new RepertoireSpec("notauser", TEST_CONTEXT, TEST_REP);
		RepertoireSpec specTo = new RepertoireSpec("notauser2", TEST_CONTEXT, TEST_REP);
		AdminOps.MoveCopyParams params = new AdminOps.MoveCopyParams(specFrom, specTo);

		ReceiveResult result = AdminOps.copyRepertoireAsync(store.get(), params).get();
		Assert.assertEquals(ReceiveResult.Error, result);
	}

	@Test
	public void copyDup() throws Exception {
		
		RepertoireSpec spec = new RepertoireSpec(TEST_USER, TEST_CONTEXT, TEST_REP);
		AdminOps.MoveCopyParams params = new AdminOps.MoveCopyParams(spec, spec);

		ReceiveResult result = AdminOps.copyRepertoireAsync(store.get(), params).get();
		Assert.assertEquals(ReceiveResult.Exists, result);
	}

	@Test
	public void copyEasy() throws Exception {
		copyEasyHelper(new RepertoireSpec(TEST_USER, TEST_CONTEXT, "easy"));
		copyEasyHelper(new RepertoireSpec(TEST_USER, "easyctx", "easy"));
		copyEasyHelper(new RepertoireSpec("easyuser", "easyctx", "easy"));
	}

	private void copyEasyHelper(RepertoireSpec specTo) throws Exception {

		RepertoireSpec specFrom = new RepertoireSpec(TEST_USER, TEST_CONTEXT, TEST_REP);
		AdminOps.MoveCopyParams params = new AdminOps.MoveCopyParams(specFrom, specTo);
		
		ReceiveResult result = AdminOps.copyRepertoireAsync(store.get(), params).get();
		Assert.assertEquals(ReceiveResult.OK, result);
		assertRepertoire(specTo);
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private void assertRepertoire(RepertoireSpec spec) {
		Repertoire thisRep = store.findRepertoire(spec);
		Helpers.assertRepertoire(sourceRep, thisRep, true);
	}
	
	private static Helpers.TempRepertoireStore store;
	private static Repertoire sourceRep;

}
