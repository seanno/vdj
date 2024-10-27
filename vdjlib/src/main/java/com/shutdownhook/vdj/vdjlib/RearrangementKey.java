//
// REARRANGEMENTKEY.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class RearrangementKey
{
	// +---------+
	// | KeyType |
	// +---------+

	public static enum KeyType
	{
		Rearrangement,
		AminoAcid,
		CDR3
	}

	// +--------------+
	// | Extractor    |
	// | getExtractor |
	// +--------------+

	public interface Extractor {
		public String extract(Rearrangement r);
	}

	public static Extractor getExtractor(KeyType keyType) {

		switch (keyType) {
			
			case CDR3:
				return(new Extractor() {
					public String extract(Rearrangement r) { return(r.getCDR3()); } });
				
			case AminoAcid:
				return(new Extractor() {
					public String extract(Rearrangement r) { return(r.AminoAcid); } });
				
			case Rearrangement:
				return(new Extractor() {
					public String extract(Rearrangement r) { return(r.Rearrangement); } });
				
			default:
				return(null);
		}
	}

	// +------------+
	// | Matcher    |
	// | getMatcher |
	// +------------+

	public interface Matcher {
		public boolean matches(String searchString, String keyString);
	}

	public static Matcher getMatcher(KeyType keyType, int allowedMutations, boolean fullLength) {

		// FUTURE --- this is a tough one. We see nucleotides with plenty of N runs at the edges,
		// like up to 15 or so. If we treat N as wild we will match these like crazy. For now just
		// shut it off, but we may want to give the option or think about another way to do better
		// in the future.
		boolean nIsWild = false;
		
		return(new Matcher() {

			public boolean matches(String search, String key) {

				if (search == null || search.isEmpty()) return(false);
				if (key == null || key.isEmpty()) return(false);

				int cchSearch = search.length();
				int cchKey = key.length();

				if (fullLength && (cchSearch != cchKey)) return(false);

				int ichStart = 0;
				int ichKeyMac = cchKey - cchSearch + 1;

				if (ichKeyMac <= 0) return(false);
		
				while (ichStart < ichKeyMac) {

					int mutsRemaining = allowedMutations;
					int j = 0;
					while (j < cchSearch) {

						char chKey = key.charAt(ichStart + j);
						char chSearch = search.charAt(j);
						
						if ((chKey != chSearch) &&
							(!nIsWild || (chKey != 'N' && chSearch != 'N'))) {

							// chars don't match and either N's are NOT wild (AminoAcid)
							// or neither character is an N ==> mismatch
							
							if (mutsRemaining == 0) break;
							--mutsRemaining;
						}
				
						++j;
					}
			
					if (j == cchSearch) return(true);
			
					++ichStart;
				}

				return(false);
			}
		});
	}
	
	// +---------+
	// | Members |
	// +---------+

	private final static Logger log = Logger.getLogger(RearrangementKey.class.getName());
}

