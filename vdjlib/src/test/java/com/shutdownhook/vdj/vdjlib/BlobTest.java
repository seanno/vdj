//
// BLOBTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.util.concurrent.CompletableFuture;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class BlobTest
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private final static String DOCKER_PULL =
		"docker pull mcr.microsoft.com/azure-storage/azurite";
	
	private final static String DOCKER_RUN_FMT =
		"docker run -d -p %s:10000 mcr.microsoft.com/azure-storage/azurite " +
		"azurite-blob --blobHost 0.0.0.0 --blobPort 10000 " +
		"--debug /tmp/azurite.log --inMemoryPersistence";

	private final static String DOCKER_STOP_FMT =
		"docker stop %s";

	private final static String AZURITE_CONNECTION_FMT =
		"DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;" +
		"AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;" +
		"BlobEndpoint=http://127.0.0.1:%s/devstoreaccount1;";

	private static int DOCKER_PORT;
	private static String DOCKER_ID;

	private static RepertoireStore store;
	
	@BeforeClass
	public static void beforeClass() throws Exception {

		DOCKER_PORT = 10000 + ((int) (Math.random() * 1000));
		String runCmd = String.format(DOCKER_RUN_FMT, DOCKER_PORT);

		System.out.println("Refreshing and starting Azurite docker container");
		Helpers.stringFromProcess(DOCKER_PULL);
		DOCKER_ID = Helpers.stringFromProcess(runCmd).trim();
		Thread.sleep(3 * 1000); // give it time to start up!

		RepertoireStore_Blobs.Config cfg = new RepertoireStore_Blobs.Config();
		cfg.ConnectionString = String.format(AZURITE_CONNECTION_FMT, DOCKER_PORT);
		store = new RepertoireStore_Blobs(cfg);
 	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		store = null;
		System.out.println("Shutting down Azurite docker container");
		Helpers.stringFromProcess(String.format(DOCKER_STOP_FMT, DOCKER_ID));
	}

	// +-------+
	// | basic |
	// +-------+

	private static String TEST_USER = "foo@bar.com";
	private static String TEST_CONTEXT = "context";

	@Test
	public void basic() throws Exception {

		SideLoadedTsv truth = SideLoadedTsv.getTsv(SideLoadedTsv.TEST_V3_TCRB);
		String name = truth.getResourceName();
		
		Helpers.ResourceStreamReader rdr = new Helpers.ResourceStreamReader(name);
		
		CompletableFuture<Boolean> future =
			TsvReceiver.receive(rdr.get(), store, TEST_USER, TEST_CONTEXT, name);

		Assert.assertTrue(future.get());

		Repertoire[] reps = store.getContextRepertoires(TEST_USER, TEST_CONTEXT);
		truth.assertRepertoire(Repertoire.find(reps, name));

		rdr.close();

		String[] contexts = store.getUserContexts(TEST_USER);
		Assert.assertEquals(1, contexts.length);
		Assert.assertEquals(TEST_CONTEXT, contexts[0]);
		
		Assert.assertTrue(store.deleteRepertoire(TEST_USER, TEST_CONTEXT, name));
		reps = store.getContextRepertoires(TEST_USER, TEST_CONTEXT);
		Assert.assertNull(Repertoire.find(reps, name));
	}
 }
