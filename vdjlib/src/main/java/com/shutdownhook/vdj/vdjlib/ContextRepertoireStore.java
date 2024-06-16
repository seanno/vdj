//
// CONTEXTREPERTOIRESTORE.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.IOException;
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


