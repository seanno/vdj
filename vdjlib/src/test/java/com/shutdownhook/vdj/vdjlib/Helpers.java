//
// HELPERS.JAVA

package com.shutdownhook.vdj.vdjlib;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assert;

import com.shutdownhook.vdj.vdjlib.model.LocusGroup;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;
import com.shutdownhook.vdj.vdjlib.TsvReceiver.ReceiveResult;

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
	
	// +-------------------+
	// | stringFromProcess |
	// +-------------------+

	public static String stringFromProcess(String cmdLine) throws Exception {
		String[] commands = new String[] { "bash", "-c", cmdLine};
		ProcessBuilder pb = new ProcessBuilder(commands);
		Process p = pb.start();

		String output = new String(p.getInputStream().readAllBytes(),
								   StandardCharsets.UTF_8);
		return(output);
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

		public void addFromResource(RepertoireSpec spec) throws Exception {
			Helpers.ResourceStreamReader rdr = new Helpers.ResourceStreamReader(spec.Name);
			ReceiveResult result = TsvReceiver.receive(rdr.get(), store, spec).get();
			if (!ReceiveResult.OK.equals(result)) throw new Exception("crap");
			rdr.close();
		}

		public Repertoire findRepertoire(RepertoireSpec spec) {
			return(Helpers.findRepertoire(store, spec));
		}

		private Path path;
		private RepertoireStore_Files store;
	}
	
	// +------------------+
	// | assertRepertoire |
	// +------------------+
	
	public static void assertRepertoire(Repertoire rE, Repertoire rT, boolean ignoreName) {
		
		if (!ignoreName) Assert.assertEquals(rT.Name, rE.Name);
		
		Assert.assertEquals(rT.TotalMilliliters, rE.TotalMilliliters, 0.000001);
		Assert.assertEquals(rT.TotalCells, rE.TotalCells);
		Assert.assertEquals(rT.TotalCount, rE.TotalCount);
		Assert.assertEquals(rT.TotalUniques, rE.TotalUniques);

		Assert.assertEquals(rT.LocusCounts.size(), rE.LocusCounts.size());
		
		for (LocusGroup group : rT.LocusCounts.keySet()) {
			Assert.assertEquals(rT.LocusCounts.get(group), rE.LocusCounts.get(group));
		}
	}
	
	// +----------------+
	// | findRepertoire |
	// +----------------+

	public static Repertoire findRepertoire(RepertoireStore store, RepertoireSpec spec) {
		Repertoire[] reps = store.getContextRepertoires(spec.UserId, spec.Context);
		return(Repertoire.find(reps, spec.Name));
	}
}
