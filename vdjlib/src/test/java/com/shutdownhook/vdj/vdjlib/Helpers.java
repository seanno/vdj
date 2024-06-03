//
// HELPERS.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Helpers
{
	// +------+
	// | init |
	// +------+
	
	public static void init() {

        System.setProperty("java.util.logging.config.file",
						   ClassLoader.getSystemResource("test-logging.properties").getPath());

		System.setProperty("java.util.logging.SimpleFormatter.format",
						   "[%1$tF %1$tT] [%4$-7s] %5$s %n");
    }
	
	// +----------------------+
	// | ResourceStreamReader |
	// +----------------------+
	
	public static class ResourceStreamReader implements Closeable
	{
		public ResourceStreamReader(String name) throws IOException {
			Helpers.init();
			this.stm = getClass().getClassLoader().getResourceAsStream(name);
			this.rdr = new InputStreamReader(stm);
		}

		public void close() {
			try { rdr.close(); } catch(Exception e1) { }
			try { stm.close(); } catch(Exception e2) { }
		}

		public InputStreamReader get() { return(rdr); }

		private InputStream stm;
		private InputStreamReader rdr;

	}
	
	// +---------------------+
	// | TempRepertoireStore |
	// +---------------------+

	public static class TempRepertoireStore implements Closeable
	{
		public TempRepertoireStore() throws IOException {

			Helpers.init();
			
			path = Files.createTempDirectory("tsv");

			RepertoireStore_Files.Config cfg = new RepertoireStore_Files.Config();
			cfg.BasePath = path.toString();
			store = new RepertoireStore_Files(cfg);
		}

		public void close() {
			try { Utility.recursiveDelete(path.toFile()); }
			catch (Exception e) { }
		}

		public RepertoireStore get() { return(store); }
		public Path getPath() { return(path); }

		public void addFromResource(String userId, String context, String name) throws Exception {
			Helpers.ResourceStreamReader rdr = new Helpers.ResourceStreamReader(name);
			if (!TsvReceiver.receive(rdr.get(), store, userId, context, name).get()) throw new Exception("crap");
			rdr.close();
		}

		private Path path;
		private RepertoireStore_Files store;
	}
}
