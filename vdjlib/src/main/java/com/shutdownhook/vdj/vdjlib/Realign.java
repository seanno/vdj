//
// REALIGN.JAVA
//

// NOTE THIS IS EXPERIMENTAL AS I TRY TO FIGURE OUT WTF TO DO ABOUT CRAPPY
// EOS ALIGNMENT AND INDEX DATA. IT ISN'T HOOKED UP TO ANYTHING YET.

package com.shutdownhook.vdj.vdjlib;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;


public class Realign
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public String IgBlastDirectory = "/opt/igblast";
		
		public String IgBlastCommandFormat =
			"bin/igblastn " +
			"-germline_db_V database/db/allV.fasta " +
			"-germline_db_D database/db/allD.fasta " +
			"-germline_db_J database/db/allJ.fasta " +
			"-auxiliary_data optional_file/human_gl.aux " +
			"-organism human " +
			"-outfmt '7 qstart qend' " + // note this outfmt is required
			"-query %s ";
	}

	public Realign(Config cfg) {
		this.cfg = cfg;
	}

	// +--------------+
	// | realign      |
	// | realignAsync |
	// +--------------+

	// +---------+
	// | igBlast |
	// +---------+

	private static String QUERYLINE_START = "# Query: ";
	private static String DETAILS_START = "# V-(D)-J rearrangement summary for query sequence (";

	private static int IDX_V = 0;
	private static int IDX_D = 1;
	private static int IDX_J = 2;
	
	private static String[] DETAIL_HDRS = new String[] { "Top V gene match", "Top D gene match", "Top J gene match" };

	public static class GeneState
	{
		private String call;
		private int ich;
		private int cch;
		private int idxDetails;

		public void reset() {
			call = null;
			ich = -1;
			cch = 0;
			idxDetails = -1;
		}
	}
	
	public static class BlastState
	{
		private int i;
		private String r;

		private int ichCDR3;
		private int cchCDR3;

		private GeneState[] genes;

		public void reset(String query) {

			String[] fields = query.split(" ");
			this.i = Integer.parseInt(fields[0]);
			this.r = fields[1];

			ichCDR3 = -1;
			cchCDR3 = 0;

			if (genes == null) {
				genes = new GeneState[3];
				for (int i = 0; i < genes.length; ++i) genes[i] = new GeneState();
			}

			for (int i = 0; i < genes.length; ++i) genes[i].reset();
		}

		public String toString() {

			String vCall = (genes[IDX_V].call == null ? "" : genes[IDX_V].call);
			String dCall = (genes[IDX_D].call == null ? "" : genes[IDX_D].call);
			String jCall = (genes[IDX_J].call == null ? "" : genes[IDX_J].call);
			
			int ichV = genes[IDX_V].ich;
			int ichD = genes[IDX_D].ich;
			int ichJ = genes[IDX_J].ich;
			
			int ichN1 = -1;
			int ichN2 = -1;
			
			if (ichV != -1) {
				int ichMacV = ichV + genes[IDX_V].cch;
				if (ichD != -1) {
					if (ichD > ichMacV) ichN1 = ichMacV;
				}
				else if (ichJ != -1) {
					if (ichJ > ichMacV) ichN1 = ichMacV;
				}
			}

			if (ichD != -1) {
				int ichMacD = ichD + genes[IDX_D].cch;
				if (ichJ != -1 && ichJ > ichMacD) ichN2 = ichMacD;
			}
			
			return(String.format("%d\t%s\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%d",
								 i, r,
								 vCall, dCall, jCall, 
								 ichV, ichN1, ichD, ichN2, ichJ));
		}
	}

	public void igBlast(File fastaFile, OutputStream blastStream) throws IOException {

		String cmd = String.format(cfg.IgBlastCommandFormat, fastaFile.getPath());
		String[] commands = new String[] { "bash", "-c", cmd};

		ProcessBuilder pb = new ProcessBuilder(commands);
		pb.directory(new File(cfg.IgBlastDirectory));
		pb.redirectError(ProcessBuilder.Redirect.DISCARD);
		Process p = pb.start();

		InputStream stm = null;
		InputStreamReader rdr = null;
		BufferedReader bufRead = null;
		OutputStreamWriter wtr = null;
		BufferedWriter bufWrite = null;
		
		try {
			stm = p.getInputStream();
			rdr = new InputStreamReader(stm);
			bufRead = new BufferedReader(rdr);

			wtr = new OutputStreamWriter(blastStream);
			bufWrite = new BufferedWriter(wtr);

			BlastState state = new BlastState();

			boolean lineIsDetails = false;
			String line;
			
			while ((line = bufRead.readLine()) != null) {

				if (line.startsWith(QUERYLINE_START)) {
					// capture the next rearrangement
					outputBlastState(state, bufWrite);
					state.reset(line.substring(QUERYLINE_START.length()));
				}
				else if (line.startsWith(DETAILS_START)) {
					// remember these for next line
					setDetailIndices(state, line);
					lineIsDetails = true;
				}
				else if (lineIsDetails) {
					// line following headers
					setDetails(state, line.split("\t"));
					lineIsDetails = false;
				}
				else if (line.startsWith("CDR3\t")) {
					// CDR3 info
					readCDR3(state, line);
				}
				else if (line.startsWith("V\t")) {
					// V
					setPositions(line, state.genes[IDX_V]);
				}
				else if (line.startsWith("D\t")) {
					// D
					setPositions(line, state.genes[IDX_D]);
				}
				else if (line.startsWith("J\t")) {
					// J
					setPositions(line, state.genes[IDX_J]);
				}
			}

			outputBlastState(state, bufWrite);
		}
		finally {

			if (bufWrite != null) Utility.safeClose(bufWrite);
			if (wtr != null) Utility.safeClose(wtr);
			
			if (bufRead != null) Utility.safeClose(bufRead);
			if (rdr != null) Utility.safeClose(rdr);
			if (stm != null) Utility.safeClose(stm);
		}
	}

	private void outputBlastState(BlastState state, BufferedWriter bufWrite) throws IOException {
		if (state.r == null) return;
		bufWrite.write(state.toString());
		bufWrite.newLine();
	}
	
	private void setDetailIndices(BlastState state, String line) {

		for (int i = 0; i < state.genes.length; ++i) state.genes[i].idxDetails = -1;

		int ichStart = DETAILS_START.length();
		int ichMac = line.indexOf(")", ichStart);
		
		if (ichMac == -1) {
			log.warning("missing headers list close in line: " + line);
			return;
		}
		
		String[] hdrs = line.substring(ichStart, ichMac).split(",");
				 
		for (int i = 0; i < hdrs.length; ++i) {
			String thisHdr = hdrs[i].trim();
			if (thisHdr.equals(DETAIL_HDRS[IDX_V])) state.genes[IDX_V].idxDetails = i;
			if (thisHdr.equals(DETAIL_HDRS[IDX_D])) state.genes[IDX_D].idxDetails = i;
			if (thisHdr.equals(DETAIL_HDRS[IDX_J])) state.genes[IDX_J].idxDetails = i;
		}
	}

	private void setDetails(BlastState state, String[] fields) {
		for (GeneState gene : state.genes) {
			if (gene.idxDetails == -1) continue;
			String call = fields[gene.idxDetails];
			if (!call.equals("N/A")) gene.call = call;
		}
	}

	private void setPositions(String line, GeneState gene) {
		String[] fields = line.split("\t");
		gene.ich = Integer.parseInt(fields[1]) - 1;
		gene.cch = Integer.parseInt(fields[2]) - (gene.ich + 1);
	}

	private void readCDR3(BlastState state, String line) {
		// nyi
	}
	
	// +---------+
	// | toFasta |
	// +---------+

	public void toFasta(InputStream tsvStream, OutputStream fastaStream) throws IOException {

		InputStreamReader rdr = null;
		TsvReader tsv = null;
		PrintWriter wtr = null;

		try {
			rdr = new InputStreamReader(tsvStream);
			tsv = new TsvReader(rdr, 0);
			wtr = new PrintWriter(fastaStream);

			long i = 0;
			Rearrangement r;
			
			while ((r = tsv.readNext()) != null) {
				wtr.printf(">%d %s\n%s\n", i++, r.Rearrangement, r.Rearrangement);
			}
		}
		finally {
			if (wtr != null) Utility.safeClose(wtr);
			if (tsv != null) Utility.safeClose(tsv);
			if (rdr != null) Utility.safeClose(rdr);
		}
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	public static void main(String[] args) throws Exception {

		Config cfg = new Config();
		cfg.IgBlastDirectory = args[0];

		Realign realign = new Realign(cfg);

		File repertoireFile = new File(args[1]);
		FileInputStream repertoireStream = new FileInputStream(repertoireFile);

		File fastaFile = File.createTempFile("igb", ".fasta");
		//fastaFile.deleteOnExit();
		FileOutputStream fastaStream = new FileOutputStream(fastaFile);

		realign.toFasta(repertoireStream, fastaStream);

		fastaStream.close();
		repertoireStream.close();

		realign.igBlast(fastaFile, System.out);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	
	private final static Logger log = Logger.getLogger(Realign.class.getName());
}
	
