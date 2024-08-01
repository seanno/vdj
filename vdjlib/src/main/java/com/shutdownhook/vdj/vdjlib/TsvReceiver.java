//
// TSVRECEIVER.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class TsvReceiver
{
	// +---------+
	// | receive |
	// +---------+

	public static enum ReceiveResult {
		OK,
		Exists,
		Error
	}
	
	public static class ReceiveStreams
	{
		OutputStream Stm;
		OutputStreamWriter Writer;
		BufferedWriter Buf;
	}
	
	public static CompletableFuture<ReceiveResult> receive(InputStreamReader stm, RepertoireStore store,
														   RepertoireSpec spec) {
		
		return(receive(stm, store, spec, null, null));
	}
	
	public static CompletableFuture<ReceiveResult> receive(InputStreamReader stm, RepertoireStore store,
														   RepertoireSpec spec, Long totalCells, Double sampleMillis) {
		
		CompletableFuture<ReceiveResult> future = new CompletableFuture<ReceiveResult>();

		Exec.getPool().submit(() -> {

			final ReceiveStreams streams = new ReceiveStreams();

			final TsvReader tsvReader = new TsvReader(stm, 0, new TsvReader.LinePeeker() {
				public void peek(String line) throws IOException {
					streams.Buf.write(line);
					streams.Buf.newLine();
				}
			});

			ReceiveResult result = ReceiveResult.Error;
				
			try {
				// check if already exists
				Repertoire[] repertoires = store.getContextRepertoires(spec.UserId, spec.Context);
				if (Repertoire.find(repertoires, spec.Name) != null) {
					result = ReceiveResult.Exists;
					throw new Exception("Attempted re-import of same repertoire (probably fine)");
				}
				
				streams.Stm = store.getRepertoireSaveStream(spec);
				
				if (streams.Stm == null) {
					throw new Exception(String.format("failed getting save Stream %s", spec));
				}
				
				streams.Writer = new OutputStreamWriter(streams.Stm);
				streams.Buf = new BufferedWriter(streams.Writer);

				Repertoire repertoire = new Repertoire();
				repertoire.Name = spec.Name;
				
				Rearrangement r;

				while ((r = tsvReader.readNext()) != null) {
					repertoire.accumulateCount(r.Locus, r.Count);
				}

				if (totalCells != null) {
					repertoire.TotalCells = totalCells;
				}
				else if (repertoire.TotalCells == 0) {
					Long cells = tsvReader.getDiscoveredCellCount();
					if (cells != null) repertoire.TotalCells = cells;
				}

				if (sampleMillis != null) {
					repertoire.TotalMilliliters = sampleMillis;
				}
				else if (tsvReader.getDiscoveredSampleMillis() != null) {
					repertoire.TotalMilliliters = tsvReader.getDiscoveredSampleMillis();
				}

				store.commitRepertoireToContext(spec.UserId, spec.Context, repertoire);
				result = ReceiveResult.OK;
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "receive", true));
			}
			finally {
				if (streams.Buf != null) Utility.safeClose(streams.Buf);
				if (streams.Writer != null) Utility.safeClose(streams.Writer);
				if (streams.Stm != null) Utility.safeClose(streams.Stm);
				if (tsvReader != null) Utility.safeClose(tsvReader);

				// only try to clean up error, not ok or already existing rep
				if (result.equals(ReceiveResult.Error)) store.deleteRepertoire(spec);
			}
			
			future.complete(result);
		});

		return(future);
	}

	private final static Logger log = Logger.getLogger(TsvReceiver.class.getName());
}
