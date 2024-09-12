//
// EXPORT.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class Export
{
	// +------------------+
	// | Setup & Teardown |
	// +------------------+

	public static class Config
	{
		public String TempPath;
		public Integer FastaLineMax = 79;
	}

	public Export() {
		this(new Config());
	}
	
	public Export(Config cfg) {
		this.cfg = cfg;
	}

	// +--------+
	// | Params |
	// +--------+

	public static enum Format
	{
		Original("tsv"),
		FastaIndex("fasta"),
		FastaHash("fasta");

		private Format(String ext) { this.ext = ext; }
		public String getExtension() { return(ext); }
		private String ext;
	}

	public static class Params
	{
		public ContextRepertoireStore CRS;
		public String Repertoire;
		public Format Format;
	}
	
	// +-------------+
	// | exportAsync |
	// | export      |
	// +-------------+

	public CompletableFuture<File> exportAsync(Params params) {

		CompletableFuture<File> future = new CompletableFuture<File>();

		Exec.getPool().submit(() -> {

			try {
				future.complete(export(params));
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "exportAsync", true));
				future.complete(null);
			}
		});

		return(future);
	}

	private File export(Params params) throws IOException {

		OutputStream out = null;
		InputStream in = null;
		File file = null;

		try {
			file = Utility.getTempFile(cfg.TempPath);
			out = new FileOutputStream(file, false);
			in = params.CRS.getRepertoireStream(params.Repertoire);
			
			switch (params.Format) {
				case Original:
					in.transferTo(out);
					break;
					
				case FastaIndex:
				case FastaHash:
					exportFasta(params, in, out);
					break;
			}
		}
		finally {
			Utility.safeClose(in);
			Utility.safeClose(out);
		}
		
		return(file);
	}

	// +-------------+
	// | exportFasta |
	// +-------------+

	private void exportFasta(Params params, InputStream in, OutputStream out) throws IOException {

		OutputStreamWriter writer = null;
		BufferedWriter buf = null;
		InputStreamReader reader = null;
		TsvReader tsv = null;

		try {
			reader = new InputStreamReader(in);
			tsv = new TsvReader(reader, 0);
			
			writer = new OutputStreamWriter(out);
			buf = new BufferedWriter(writer);

			Rearrangement r;
			int i = 0;

			while ((r = tsv.readNext()) != null) {

				buf.write(">" + (params.Format.equals(Format.FastaIndex)
								 ? Integer.toString(i++)
								 : Utility.sha256(r.Rearrangement)));

				buf.newLine();

				int ich = 0;
				int cch = r.Rearrangement.length();

				while (ich < cch) {

					int ichMac = ich + cfg.FastaLineMax;
					if (ichMac > cch) ichMac = cch;

					buf.write(r.Rearrangement, ich, ichMac - ich);
					buf.newLine();
					
					ich = ichMac;
				}
			}

		}
		finally {
			Utility.safeClose(tsv);
			Utility.safeClose(reader);
			Utility.safeClose(buf);
			Utility.safeClose(writer);
		}
	}

	// +---------+
	// | Members |
	// +---------+

    private Config cfg;
							   
	private final static Logger log = Logger.getLogger(Export.class.getName());
}
