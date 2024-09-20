//
// ADMINOPS.JAVA
//

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.TsvReceiver.ReceiveResult;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class AdminOps
{
	// +---------------------+
	// | moveRepertoire      |
	// | moveRepertoireAsync |
	// +---------------------+

	public static CompletableFuture<Boolean>
		moveRepertoireAsync(RepertoireStore store, MoveCopyParams params) {

		CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();

		Exec.getPool().submit(() -> {
			try {
				boolean success = moveRepertoire(store, params);
				future.complete(success);
			}
			catch (Exception e) {
				log.severe(Utility.exMsg(e, "moveRepertoireAsync", true));
				future.complete(false);
			}
		});

		return(future);
	}

	public static boolean
		moveRepertoire(RepertoireStore store, MoveCopyParams params) throws Exception {

		ReceiveResult result = copyRepertoire(store, params);
		if (result != ReceiveResult.OK) return(false);

		boolean success = store.deleteRepertoire(params.From);

		if (!success) log.warning(String.format("copied rep ok but failed delete (%s)", params));

		return(success);
	}

	// +---------------------+
	// | copyRepertoire      |
	// | copyRepertoireAsync |
	// +---------------------+

	public static CompletableFuture<ReceiveResult>
		copyRepertoireAsync(RepertoireStore store, MoveCopyParams params) {

		CompletableFuture<ReceiveResult> future = new CompletableFuture<ReceiveResult>();

		Exec.getPool().submit(() -> {
			try {
				ReceiveResult result = copyRepertoire(store, params);
				future.complete(result);
			}
			catch (Exception e) {
				log.severe(Utility.exMsg(e, "copyRepertoireAsync", true));
				future.complete(ReceiveResult.Error);
			}
		});

		return(future);
	}
	
	public static ReceiveResult
		copyRepertoire(RepertoireStore store, MoveCopyParams params ) throws Exception {

		Repertoire[] repertoires = store.getContextRepertoires(params.From.UserId, params.From.Context);
		Repertoire repFrom = Repertoire.find(repertoires, params.From.Name);

		InputStream stm = null;
		InputStreamReader rdr = null;

		try {
			stm = store.getRepertoireStream(params.From);
			rdr = new InputStreamReader(stm);

			return(TsvReceiver.receive(rdr, store, new RepertoireSpec(params.To, params.From),
									   repFrom.TotalCells, repFrom.TotalMilliliters, repFrom.Date).get());
		}
		finally {
			if (rdr != null) Utility.safeClose(rdr);
			if (stm != null) Utility.safeClose(stm);
		}
	}

	// +----------------+
	// | MoveCopyParams |
	// +----------------+

	public static class MoveCopyParams
	{
		public MoveCopyParams() {
			From = new RepertoireSpec();
			To = new RepertoireSpec();
		}

		public MoveCopyParams(RepertoireSpec from, RepertoireSpec to) {
			From = from;
			To = to;
		}
		
		public RepertoireSpec From = new RepertoireSpec();
		public RepertoireSpec To = new RepertoireSpec();
		
		public String toString() { return(String.format("%s -> %s", From, To)); }
	}

	// +---------+
	// | Helpers |
	// +---------+
		
	private final static Logger log = Logger.getLogger(AdminOps.class.getName());
}
