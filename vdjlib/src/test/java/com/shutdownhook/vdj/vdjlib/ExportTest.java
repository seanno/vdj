//
// EXPORTTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.shutdownhook.vdj.vdjlib.Export;

public class ExportTest 
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	private static String TEST_USER = "foo@bar.com";
	private static String TEST_CONTEXT = "context";

	private static String REP_1 = "subject9-v2.tsv";
	private static String REP_2 = "BH.tsv";
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		
		store = new Helpers.TempRepertoireStore();
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_1));
		store.addFromResource(new RepertoireSpec(TEST_USER, TEST_CONTEXT, REP_2));
		crs = new ContextRepertoireStore(store.get(), TEST_USER, TEST_CONTEXT);
	}
	
	@AfterClass
	public static void afterClass() {
		store.close();
	}

	// +-----------------+
	// | testFastaExport |
	// +-----------------+

	@Test
	public void testFastaExport() throws Exception {
		testFastaExportHelper(REP_1, Export.Format.FastaIndex, 79);
		testFastaExportHelper(REP_2, Export.Format.FastaHash, 15);
	}
	
	private void testFastaExportHelper(String name, Export.Format format, int fastaLineMax) throws Exception {

		File file = export(name, format, fastaLineMax);
		String actual = Files.readString(file.toPath());
		file.delete();

		InputStream stm = getClass().getClassLoader().getResourceAsStream(name + ".fasta");
		String expected = new String(stm.readAllBytes(), "UTF-8");
		stm.close();

		Assert.assertEquals(expected, actual);
	}

	// +---------------+
	// | testTsvExport |
	// +---------------+

	@Test
	public void testTsvExport() throws Exception {
		testTsvExportHelper(REP_1);
		testTsvExportHelper(REP_2);
	}

	private void testTsvExportHelper(String name) throws Exception {
		
		File file = export(name, Export.Format.Original, 0);
		String actual = Files.readString(file.toPath());
		file.delete();

		InputStream stm = crs.getRepertoireStream(name);
		String expected = new String(stm.readAllBytes(), "UTF-8");
		stm.close();

		Assert.assertEquals(expected, actual);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private File export(String name, Export.Format format, int fastaLineMax) throws Exception {

		Export.Config cfg = new Export.Config();
		cfg.FastaLineMax = fastaLineMax;
		Export export = new Export(cfg);

		Export.Params params = new Export.Params();
		params.CRS = crs;
		params.Repertoire = name;
		params.Format = format;
		
		return(export.exportAsync(params).get());
	}

	// +---------+
	// | Members |
	// +---------+

	private static Helpers.TempRepertoireStore store;
	private static ContextRepertoireStore crs;
}
