//
// REPERTOIRESTORE_FILES.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class RepertoireStore_Files implements RepertoireStore 
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String BasePath;
		public String ContextFileName = "context.json";
	}

	public RepertoireStore_Files(Config cfg) {
		
		this.cfg = cfg;
		
		this.baseDir = new File(cfg.BasePath);
		this.baseDir.mkdirs();
	}

	// +-----------------+
	// | getUserContexts |
	// +-----------------+
	
	public String[] getUserContexts(String userId) {

		File[] files = getUserDir(userId).listFiles(new FileFilter() {
			public boolean accept(File candidate) { return(candidate.isDirectory()); }
		});

		String[] contexts = new String[files.length];
		for (int i = 0; i < files.length; ++i) contexts[i] = files[i].getName();

		return(contexts);
	}

	// +-----------------------+
	// | getContextRepertoires |
	// +-----------------------+
	
	public Repertoire[] getContextRepertoires(String userId, String ctx) {

		try {
			File contextFile = getContextFile(userId, ctx);
			String contextJson = Easy.stringFromFile(contextFile.getAbsolutePath());
			return(Repertoire.fromJsonArray(contextJson));
		}
		catch (FileNotFoundException eNotFound) {
			return(new Repertoire[0]);
		}
		catch (IOException e) {
			
			String msg = String.format("getContextRepertoires %s/%s", userId, ctx);
			log.severe(Easy.exMsg(e, msg, true));
			return(null);
		}
	}

	// +---------------------+
	// | getRepertoireStream |
	// +---------------------+
	
	public InputStream getRepertoireStream(String userId, String ctx, String rep) {

		try {
			return(new FileInputStream(getRepertoireFile(userId, ctx, rep)));
		}
		catch (IOException e) {

			String msg = String.format("getRepertoireStream %s/%s/%s", userId, ctx, rep);
			log.severe(Easy.exMsg(e, msg, true));
			return(null);
		}
	}

	// +-------------------------+
	// | getRepertoireSaveStream |
	// +-------------------------+

	public OutputStream getRepertoireSaveStream(String userId, String ctx, String rep) {

		try {
			File tsvFile = getRepertoireFile(userId, ctx, rep);
			
			if (tsvFile.exists()) {
				log.warning(String.format("File for rep %s/%s/%s already exists, " +
										  "name collision?", userId, ctx, rep));
				return(null);
			}
			
			return(new FileOutputStream(tsvFile));
		}
		catch (IOException e) {

			String msg = String.format("getRepertoireSaveStream %s/%s/%s", userId, ctx, rep);
			log.severe(Easy.exMsg(e, msg, true));
			return(null);
		}
	}
	
	// +---------------------------+
	// | commitRepertoireToContext |
	// +---------------------------+

	public boolean commitRepertoireToContext(String userId, String ctx, Repertoire r) {

		Repertoire[] oldReps = getContextRepertoires(userId, ctx);
		Repertoire[] newReps = new Repertoire[oldReps.length + 1];

		for (int i = 0; i < oldReps.length; ++i) newReps[i] = oldReps[i];
		newReps[oldReps.length] = r;

		String newJson = Repertoire.toJsonArray(newReps);

		try {
			File contextFile = getContextFile(userId, ctx);
			Easy.stringToFile(contextFile.getAbsolutePath(), newJson);
			return(true);
		}
		catch (IOException e) {
			String msg = String.format("commitRepertoireToContext %s/%s", userId, ctx);
			log.severe(Easy.exMsg(e, msg, true));
			return(false);
		}
	}
	
	// +------------------+
	// | deleteRepertoire |
	// +------------------+

	public boolean deleteRepertoire(String userId, String ctx, String rep) {

		removeRepertoireFromContext(userId, ctx, rep);

		boolean success = false;
		
		try {
			getRepertoireFile(userId, ctx, rep).delete();
			success = true;
		}
		catch (Exception e) {
			log.warning(String.format("Exception deleting rep %s/%s/%s", userId, ctx, rep));
		}
				
		return(success);
	}

	private void removeRepertoireFromContext(String userId, String ctx, String rep) {

		Repertoire[] oldReps = getContextRepertoires(userId, ctx);

		int irepFound;
		for (irepFound = 0; irepFound < oldReps.length; ++irepFound) {
			if (oldReps[irepFound].Name.equals(rep)) break;
		}

		if (irepFound == oldReps.length) return;

		Repertoire[] newReps = new Repertoire[oldReps.length - 1];

		int irepNew = 0;
		for (int irep = 0; irep < oldReps.length; ++irep) {
			if (irep != irepFound) newReps[irepNew++] = oldReps[irep];
		}

		String newJson = Repertoire.toJsonArray(newReps);

		try {
			File contextFile = getContextFile(userId, ctx);
			Easy.stringToFile(contextFile.getAbsolutePath(), newJson);
		}
		catch (IOException e) {
			String msg = String.format("removeRepertoireFromContext %s/%s", userId, ctx);
			log.warning(Easy.exMsg(e, msg, true));
		}
	}

	// +---------+
	// | Helpers |
	// +---------+

	// IMPORTANT: the reason we append a hash here is for edge-case security,
	// to ensure similar userIds (se:an se_an) don't resolve to the same directory.

	private File getUserDir(String userId) {
		File userDir = new File(baseDir, clean(userId) + "_" + Easy.sha256(userId));
		userDir.mkdirs();
		return(userDir);
	}

	private File getContextDir(String userId, String ctx) {
		File contextDir = new File(getUserDir(userId), ctx);
		contextDir.mkdirs();

		return(contextDir);
	}

	private File getContextFile(String userId, String ctx) {
		return(new File(getContextDir(userId, ctx), cfg.ContextFileName));
	}

	private File getRepertoireFile(String userId, String ctx, String rep) {
		return(new File(getContextDir(userId, ctx), clean(rep) + ".tsv"));
	}

	private String clean(String input) {
		return(input.replaceAll("\\W+", "_"));
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private File baseDir;

	private static Gson gson = new Gson();

	private final static Logger log = Logger.getLogger(RepertoireStore_Files.class.getName());
}
