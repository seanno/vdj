//
// MRDENGINE.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.RearrangementKey.Extractor;
import com.shutdownhook.vdj.vdjlib.RearrangementKey.Matcher;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class MrdEngine
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public Integer MinMatchLength = 25;
	}

	public MrdEngine(Config cfg) {
		this.cfg = cfg;
	}
	
	// +--------------+
	// | getExtractor |
	// +--------------+

	public Extractor getExtractor() {
		return(new Extractor() {
			public String extract(Rearrangement r) {
				return(Integer.toString(getIchJ(r)) + ":" + r.Rearrangement);
			}
		});
	}
	
	// +------------+
	// | getMatcher |
	// +------------+

	public static class ParsedMrdString
	{
		public int IchJ;
		public String Rearrangement;
	}
				
	public Matcher getMatcher() {
		
		return(new Matcher() {
				
			public boolean matches(String searchString, String keyString) {
				
				ParsedMrdString s = parseMrdString(searchString);
				ParsedMrdString k = parseMrdString(keyString);

				return(match(s.Rearrangement, s.IchJ, k.Rearrangement, k.IchJ));
			}

			private ParsedMrdString parseMrdString(String input) {
				ParsedMrdString parsed = new ParsedMrdString();
				parsed.IchJ = 0;

				int cch = input.length();
				int ichWalk = 0;

				while (ichWalk < cch) {
					char ch = input.charAt(ichWalk);
					if (ch == ':') break;

					parsed.IchJ *= 10;
					parsed.IchJ += ((int)ch) - ((int)'0');
					++ichWalk;
				}

				parsed.Rearrangement = input.substring(ichWalk + 1);
				return(parsed);
			}

		});
	}
	
	// +-------+
	// | match |
	// +-------+

	// since mrd tracking is done across multiple assay versions, we detect matches
	// by aligning on the J index and scanning left and right --- if the rearrangements
	// fully match the length of the shorter of the two seqeunces we call it good.
	// At least the last time I looked at it, the Adaptive version did not impose a
	// minimum match, which caused "compression" errors where very short sequences
	// over-matched; so we parameterize that here. Note if either rearrangment doesn't
	// call a J index, we just match from the J side edge.

	public boolean match(Rearrangement r1, Rearrangement r2) {
		
		return(match(r1.Rearrangement, getIchJ(r1), r2.Rearrangement, getIchJ(r2)));
	}
	
	public boolean match(String r1, int ichJ1, String r2, int ichJ2) {

		// align on J index (or J edge if necessary)
		int cch1 = r1.length();
		int cch2 = r2.length();

		int ichJ1Real = ichJ1;
		int ichJ2Real = ichJ2;
		
		// if we don't have a valid j index for either rearrangement, just match
		// from the right side. Doesn't help to know one and not the other
		if (ichJ1 < 0 || ichJ1 >= cch1 || ichJ2 < 0 || ichJ2 >= cch2) {
			ichJ1Real = cch1;
			ichJ2Real = cch2;
		}

		boolean match = true;
		int cchMatch = 0;
		char ch1, ch2;

		// search right
		int ich1 = ichJ1Real;
		int ich2 = ichJ2Real;

		while (ich1 < cch1 && ich2 < cch2) {
			ch1 = Character.toLowerCase(r1.charAt(ich1));
			ch2 = Character.toLowerCase(r2.charAt(ich2));
			if (ch1 != ch2) { match = false; break; }
			ich1++; ich2++; cchMatch++;
		}

		if (!match) return(false);
		
		// and left
		ich1 = ichJ1Real - 1;
		ich2 = ichJ2Real - 1;

		while (ich1 >= 0 && ich2 >= 0) {
			ch1 = Character.toLowerCase(r1.charAt(ich1));
			ch2 = Character.toLowerCase(r2.charAt(ich2));
			if (ch1 != ch2) { match = false; break; }
			ich1--; ich2--; cchMatch++;
		}

		// and out
		return(match && cchMatch >= cfg.MinMatchLength);
	}

	// +---------+
	// | Helpers |
	// +---------+
		
	private static int getIchJ(Rearrangement r) {
		int cch = r.Rearrangement.length();
		return(r.JIndex < 0 || r.JIndex > cch ? cch : r.JIndex);
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(MrdEngine.class.getName());
}

