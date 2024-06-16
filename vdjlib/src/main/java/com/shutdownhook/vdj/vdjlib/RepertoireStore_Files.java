//
// REPERTOIRESTORE_FILES.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.logging.Logger;

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
			String contextJson = Files.readString(contextFile.toPath());
			return(Repertoire.fromJsonArray(contextJson));
		}
		catch (NoSuchFileException eNotFound) {
			return(new Repertoire[0]);
		}
		catch (IOException e) {
			
			String msg = String.format("getContextRepertoires %s/%s", userId, ctx);
			log.severe(Utility.exMsg(e, msg, true));
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
			log.severe(Utility.exMsg(e, msg, true));
			return(null);
		}
	}

	// +-------------------------+
	// | getRepertoireSaveStream |
	// +-------------------------+

	public OutputStream getRepertoireSaveStream(String userId, String ctx, String rep) {

		try {
			getContextDir(userId, ctx).mkdirs();
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
			log.severe(Utility.exMsg(e, msg, true));
			return(null);
		}
	}
	
	// +---------------------------+
	// | commitRepertoireToContext |
	// +---------------------------+

	public boolean commitRepertoireToContext(String userId, String ctx, Repertoire r) {

		try {
			Repertoire[] newReps = Repertoire.append(getContextRepertoires(userId, ctx), r);
			saveContextRepertoires(userId, ctx, newReps);
			return(true);
		}
		catch (IOException e) {
			String msg = String.format("commitRepertoireToContext %s/%s", userId, ctx);
			log.severe(Utility.exMsg(e, msg, true));
			return(false);
		}
	}
	
	// +------------------+
	// | deleteRepertoire |
	// +------------------+

	public boolean deleteRepertoire(String userId, String ctx, String name) {

		try {
			// remove from context
			Repertoire[] newReps = Repertoire.remove(getContextRepertoires(userId, ctx), name);
			if (newReps.length == 0) {
				getContextFile(userId, ctx).delete();
			}
			else {
				saveContextRepertoires(userId, ctx, newReps);
			}

			// remove file
			getRepertoireFile(userId, ctx, name).delete();

			// remove context dir if this was the last repertoire
			if (newReps.length == 0) {
				try {
					Utility.recursiveDelete(getContextDir(userId, ctx));
				}
				catch (Exception eDir) {
					log.warning(Utility.exMsg(eDir, "context dir delete (non-fatal)", false));
				}
			}

			return(true);
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "deleteRepertoire", true));
			return(false);
		}
	}

	// +---------+
	// | Helpers |
	// +---------+

	// IMPORTANT: the reason we append a hash here is for edge-case security,
	// to ensure similar userIds (se:an se_an) don't resolve to the same directory.

	private File getUserDir(String userId) {
		File userDir = new File(baseDir, clean(userId) + "_" + Utility.sha256(userId));
		userDir.mkdirs();
		return(userDir);
	}

	private File getContextDir(String userId, String ctx) {
		return(new File(getUserDir(userId), ctx));
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

	private void saveContextRepertoires(String userId, String ctx, Repertoire[] reps) throws IOException {
		String json = Repertoire.toJsonArray(reps);
		getContextDir(userId, ctx).mkdirs();
		File contextFile = getContextFile(userId, ctx);
		Utility.stringToFile(contextFile.getAbsolutePath(), json);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private File baseDir;

	private final static Logger log = Logger.getLogger(RepertoireStore_Files.class.getName());
}
