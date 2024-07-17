//
// CONTEXTREPERTOIRESTORE.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class ContextRepertoireStore
{
	public ContextRepertoireStore(RepertoireStore store, String userId, String context) {
		this.store = store;
		this.userId = userId;
		this.context = context;
	}
	
	// +----------------+
	// | getRepertoires |
	// | findRepertoire |
	// +----------------+
	
	public Repertoire[] getRepertoires() throws IOException {
		ensureRepertoires();
		return(repertoires);
	}

	public synchronized Repertoire findRepertoire(String name) throws IOException {
		
		ensureRepertoires();
		Repertoire rep = Repertoire.find(repertoires, name);

		if (rep == null) {
			log.warning(String.format("Repertoire %s not found in %s/%s",
									  rep, userId, context));
		}
		
		return(rep);
	}

	// +---------------------+
	// | getRepertoireStream |
	// +---------------------+

	public InputStream getRepertoireStream(String rep) throws IOException {
		return(store.getRepertoireStream(userId, context, rep));
	}
	
	public InputStream getRepertoireStream(Repertoire rep) throws IOException {
		return(store.getRepertoireStream(userId, context, rep.Name));
	}

	// +-----------------+
	// | Secondary Files |
	// +-----------------+

	public InputStream getSecondaryStream(String rep, String key) {
		return(store.getRepertoireSecondaryStream(userId, context, rep, key));
	}

	public InputStream getSecondaryStream(Repertoire rep, String key) {
		return(store.getRepertoireSecondaryStream(userId, context, rep.Name, key));
	}

	public OutputStream getSecondarySaveStream(String rep, String key) {
		return(store.getRepertoireSecondarySaveStream(userId, context, rep, key));
	}
	
	public OutputStream getSecondarySaveStream(Repertoire rep, String key) {
		return(store.getRepertoireSecondarySaveStream(userId, context, rep.Name, key));
	}

	public void saveSecondaryFile(String rep, String key, File file) throws IOException {

		OutputStream output = null;

		try {
			output = getSecondarySaveStream(rep, key);
			if (output == null) return; // this is ok, just means we don't have a cache
			Files.copy(file.toPath(), output);
		}
		finally {
			if (output != null) output.close();
		}
	}

	// +---------+
	// | Helpers |
	// +---------+
	
	public synchronized void ensureRepertoires() throws IOException {
		if (repertoires == null) {
			repertoires = store.getContextRepertoires(userId, context);
		}
	}

	// +---------+
	// | Members |
	// +---------+

	private RepertoireStore store;
	private String userId;
	private String context;

	private Repertoire[] repertoires;

	private final static Logger log = Logger.getLogger(ContextRepertoireStore.class.getName());
}


