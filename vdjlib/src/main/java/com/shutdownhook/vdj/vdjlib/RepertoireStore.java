//
// REPERTOIRESTORE.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.OutputStream;

import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public interface RepertoireStore
{
	// IMPORTANT: It is up to implementations to perform integrity checks on the
	// methods below; e.g., iterateRepertoire must verify that REP is actually
	// in CTX which belongs to userId. Failing to do this will result in pretty
	// catastrophic security failure.

	public String[] getUserContexts(String userId);
	public Repertoire[] getContextRepertoires(String userId, String ctx);
	public InputStream getRepertoireStream(String userId, String ctx, String rep);

	// Optional methods for a store that implements uploads

	default public OutputStream // return null if already exists!
		getRepertoireSaveStream(String userId, String ctx, String rep) { return(null); }
	
	default public boolean
		commitRepertoireToContext(String userId, String ctx, Repertoire r) { return(false); }
	
	default public boolean
		deleteRepertoire(String userId, String ctx, String rep) { return(false); }
}
