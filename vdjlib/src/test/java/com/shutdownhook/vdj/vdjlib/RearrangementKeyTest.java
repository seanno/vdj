//
// REARRANGEMENTKEYTEST.JAVA

package com.shutdownhook.vdj.vdjlib;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import com.shutdownhook.vdj.vdjlib.RearrangementKey.KeyType;
import com.shutdownhook.vdj.vdjlib.RearrangementKey.Matcher;

public class RearrangementKeyTest 
{

	// +----------+
	// | Matching |
	// +----------+

	@Test
	public void testMatcher_Nucleotide_Zero_Sub() throws Exception {
		Matcher matcher = RearrangementKey.getMatcher(KeyType.Rearrangement, 0, false);
		Assert.assertTrue(matcher.matches("ABCD", "ABCDEFG"));
		Assert.assertTrue(matcher.matches("BCD", "ABCDEFG"));
		Assert.assertFalse(matcher.matches("AXXE", "ABCDEFG"));
		Assert.assertFalse(matcher.matches("EFGH", "ABCDEFG"));

		Assert.assertTrue(matcher.matches(
			 "GCCATGGGTATGGTGGCTACGCCCCGGGACCCTACGGTATGGACGTCTGGGGCCAAGGG",
			 "GCCATGGGTATGGTGGCTACGCCCCGGGACCCTACGGTATGGACGTCTGGGGCCAAGGG"));
	}
	
	@Test
	public void testMatcher_Nucleotide_Two_Sub() throws Exception {
		Matcher matcher = RearrangementKey.getMatcher(KeyType.Rearrangement, 2, false);
		Assert.assertTrue(matcher.matches("AXXDE", "ABCDEFG"));
		Assert.assertTrue(matcher.matches("GGD", "ABCDEFG"));
	}
	
	@Test
	public void testMatcher_Nucleotide_One_Sub() throws Exception {
		Matcher matcher = RearrangementKey.getMatcher(KeyType.Rearrangement, 2, false);
		Assert.assertFalse(matcher.matches("AXXE", "ABCDEFG"));
	}
}
