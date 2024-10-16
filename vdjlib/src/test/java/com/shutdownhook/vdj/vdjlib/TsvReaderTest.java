//
// TSVREADERTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class TsvReaderTest 
{
	// +-------+
	// | basic |
	// +-------+

	@Test
    public void basicV2() throws Exception {
		basic(SideLoadedTsv.TEST_V2_TCRB, 1000);
	}

	@Test
    public void basicV2IGH() throws Exception {
		basic(SideLoadedTsv.TEST_V2_IGH, 1234);
	}

	@Test
    public void basicPipelineTCRG() throws Exception {
		basic(SideLoadedTsv.TEST_PIPELINE_TCRG, 500);
	}

	@Test
    public void basicV3() throws Exception {
		basic(SideLoadedTsv.TEST_V3_TCRB, 852);
	}

	@Test
    public void basicCellfree() throws Exception {
		basic(SideLoadedTsv.TEST_CELLFREE_EOS, 20);
	}

	@Test
    public void basicIteDiff() throws Exception {
		basic(SideLoadedTsv.TEST_TCRB_ITE_DIFF, 500);
	}

    private void basic(int which, int batchSize) throws Exception {

		SideLoadedTsv truth = SideLoadedTsv.getTsv(which);
		
		String name = truth.getResourceName();
		Helpers.ResourceStreamReader rdr = new Helpers.ResourceStreamReader(name);
		TsvReader tsv = new TsvReader(rdr.get(), 0);

		int rows = 0;
		List<Rearrangement> batch;
		
		do {
			batch =	tsv.readNextBatchAsync(batchSize).get(5, TimeUnit.SECONDS);
			
			if (batch != null) {
				
				int count = batch.size();
				System.out.println(String.format("Read batch of %d rows from %s", count, name));

				truth.assertRearrangement(batch.get(0), rows);
				truth.assertRearrangement(batch.get(count - 1), rows + count - 1);
				rows += count;
			}
		}
		while (batch != null && batch.size() == batchSize);

		System.out.println(String.format("Read %d total rows from %s", rows, name));
		Assert.assertEquals(truth.getRepertoire().TotalUniques, rows);
		
		tsv.close();
		rdr.close();
	}

	// +---------+
	// | Helpers |
	// +---------+

	private void readToEnd(TsvReader tsv, String name) throws Exception {

		Rearrangement r;
		int i = 0;
		while ((r = tsv.readNext()) != null) ++i;

		System.out.println(String.format("readToEnd %d rows from %s", i, name));
	}
}
