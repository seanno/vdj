//
// MRDENGINETEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class MrdEngineTest 
{
	// +----------+
	// | mrdMatch |
	// +----------+

	@Test
	public void testMrdMatch() throws Exception {
		Assert.assertTrue("full", matchTwo("aaaaa", 2, "aaaaa", 2, 3));
		Assert.assertTrue("partial1", matchTwo("abcde", 1, "bcd", 0, 3));
		Assert.assertTrue("partial2", matchTwo("abcde", -1, "cde", -1, 3));
		Assert.assertTrue("partial3", matchTwo("abcde", 0, "aabc", 1, 3));
		Assert.assertFalse("fail1", matchTwo("abcde", 2, "fghijklmn", 2, 4));
		Assert.assertFalse("fail-length1", matchTwo("abcde", 1, "bcd", 0, 4));
		Assert.assertFalse("fail-length2", matchTwo("abcde", -1, "cde", -1, 4));
	}

	private static boolean matchTwo(String seq1, int jIndex1,
									String seq2, int jIndex2,
									int cchMatchMin)
	{
		MrdEngine.Config cfg = new MrdEngine.Config();
		cfg.MinMatchLength = cchMatchMin;
		MrdEngine mrd = new MrdEngine(cfg);
		
		Rearrangement r1 = makeMrdRearrangement(seq1, jIndex1);
		Rearrangement r2 = makeMrdRearrangement(seq2, jIndex2);
		return(mrd.match(r1, r2));
	}
									
	private static Rearrangement makeMrdRearrangement(String seq, int jIndex) {

		Rearrangement r = new Rearrangement();
		r.Rearrangement = seq;
		r.JIndex = jIndex;
		return(r);
	}

}
