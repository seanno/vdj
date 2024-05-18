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

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class TsvReceiver
{
	// +---------+
	// | receive |
	// +---------+

	public static class ReceiveStreams
	{
		OutputStream Stm;
		OutputStreamWriter Writer;
		BufferedWriter Buf;
	}
	
	public static CompletableFuture<Boolean> receive(InputStreamReader stm, RepertoireStore store,
													 String userId, String ctx, String rep) {
		
		CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();

		Exec.getPool().submit(() -> {

			final ReceiveStreams streams = new ReceiveStreams();

			final TsvReader tsvReader = new TsvReader(stm, 0, new TsvReader.LinePeeker() {
				public void peek(String line) throws IOException {
					streams.Buf.write(line);
					streams.Buf.newLine();
				}
			});
				
			try {
				streams.Stm = store.getRepertoireSaveStream(userId, ctx, rep);
				streams.Writer = new OutputStreamWriter(streams.Stm);
				streams.Buf = new BufferedWriter(streams.Writer);

				Repertoire repertoire = new Repertoire();
				repertoire.Name = rep;
				
				Rearrangement r;

				while ((r = tsvReader.readNext()) != null) {
					repertoire.accumulateCount(r.Locus, r.Count);
				}

				if (repertoire.TotalCells == 0) {
					Long cells = tsvReader.getDiscoveredCellCount();
					if (cells != null) repertoire.TotalCells = cells;
				}

				store.commitRepertoireToContext(userId, ctx, repertoire);

				future.complete(true);
			}
			catch (Exception e) {
				log.warning(Easy.exMsg(e, "receive", true));
				future.complete(false);
			}
			finally {
				if (streams.Buf != null) Easy.safeClose(streams.Buf);
				if (streams.Writer != null) Easy.safeClose(streams.Writer);
				if (streams.Stm != null) Easy.safeClose(streams.Stm);
				if (tsvReader != null) Easy.safeClose(tsvReader);
			}
			
		});

		return(future);
	}

	private final static Logger log = Logger.getLogger(TsvReceiver.class.getName());
}
