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
	
	public InputStream getRepertoireStream(RepertoireSpec spec) {

		try {
			return(new FileInputStream(getRepertoireFile(spec)));
		}
		catch (IOException e) {

			String msg = String.format("getRepertoireStream %s", spec);
			log.severe(Utility.exMsg(e, msg, true));
			return(null);
		}
	}

	// +-------------------------+
	// | getRepertoireSaveStream |
	// +-------------------------+

	public OutputStream getRepertoireSaveStream(RepertoireSpec spec) {

		try {
			getContextDir(spec.UserId, spec.Context).mkdirs();
			File tsvFile = getRepertoireFile(spec);
			
			if (tsvFile.exists()) {
				log.warning(String.format("File for rep %s already exists, name collision?", spec));
				return(null);
			}
			
			return(new FileOutputStream(tsvFile));
		}
		catch (IOException e) {

			String msg = String.format("getRepertoireSaveStream %s", spec);
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

	public boolean deleteRepertoire(RepertoireSpec spec) {

		try {
			// remove from context
			Repertoire[] oldReps = getContextRepertoires(spec.UserId, spec.Context);
			Repertoire[] newReps = Repertoire.remove(oldReps, spec.Name);
			
			if (newReps.length == 0) {
				getContextFile(spec.UserId, spec.Context).delete();
			}
			else {
				saveContextRepertoires(spec.UserId, spec.Context, newReps);
			}

			// remove file
			getRepertoireFile(spec).delete();

			if (newReps.length == 0) {
				// last repertoire; remove context dir
				try {
					Utility.recursiveDelete(getContextDir(spec.UserId, spec.Context));
				}
				catch (Exception eDir) {
					log.warning(Utility.exMsg(eDir, "context dir delete (non-fatal)", true));
				}
			}
			else {
				// make sure cached files are gone
				deleteRepertoireSecondaryFiles(spec);
			}

			return(true);
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "deleteRepertoire", true));
			return(false);
		}
	}

	// +----------------------------------+
	// | getRepertoireSecondarySaveStream |
	// +----------------------------------+

	public OutputStream getRepertoireSecondarySaveStream(RepertoireSpec spec, String key) {
		
		try {
			File cacheDir = getRepertoireCacheDir(spec);
			cacheDir.mkdirs();
			return(new FileOutputStream(new File(cacheDir, clean(key))));
		}
		catch (IOException e) {

			String msg = String.format("getRepertoireSecondarySaveStream %s/%s", spec, key);
			log.severe(Utility.exMsg(e, msg, true));
			return(null);
		}
	}

	// +------------------------------+
	// | getRepertoireSecondaryStream |
	// +------------------------------+
	
	public InputStream getRepertoireSecondaryStream(RepertoireSpec spec, String key) {

		try {
			return(new FileInputStream(new File(getRepertoireCacheDir(spec), clean(key))));
		}
		catch (FileNotFoundException eNotFound) {
			// this is ok, just means we don't have one cached
			return(null);
		}
		catch (IOException e) {
			String msg = String.format("getRepertoireSecondaryStream %s/%s", spec, key);
			log.severe(Utility.exMsg(e, msg, true));
			return(null);
		}
	}

	// +--------------------------------+
	// | deleteRepertoireSecondaryFiles |
	// +--------------------------------+
	
	public boolean deleteRepertoireSecondaryFiles(RepertoireSpec spec) {

		try {
			Utility.recursiveDelete(getRepertoireCacheDir(spec));
			return(true);
		}
		catch (Exception eDir) {
			log.warning(Utility.exMsg(eDir, "repertoire cache dir delete", false));
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

	private File getRepertoireFile(RepertoireSpec spec) {
		return(new File(getContextDir(spec.UserId, spec.Context), clean(spec.Name) + TSV_EXT));
	}

	private File getRepertoireCacheDir(RepertoireSpec spec) {
		File cacheDir = new File(getContextDir(spec.UserId, spec.Context), clean(spec.Name) + CACHE_SUFFIX);
		cacheDir.mkdirs();
		return(cacheDir);
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

	private final static String TSV_EXT = ".tsv";
	private final static String CACHE_SUFFIX = "__cache";

	private final static Logger log = Logger.getLogger(RepertoireStore_Files.class.getName());
}
