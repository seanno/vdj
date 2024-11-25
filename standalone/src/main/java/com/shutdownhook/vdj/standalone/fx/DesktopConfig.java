//
// DESKTOPCONFIG.JAVA
//

package com.shutdownhook.vdj.standalone.fx;

import java.io.File;
import java.io.IOException;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.vdj.standalone.Server;
import com.shutdownhook.vdj.vdjlib.Utility;

public class DesktopConfig
{
	// +-----------+
	// | getConfig |
	// +-----------+
	
	public static Server.Config getConfig() throws IOException {

		File cfgFile = getFile(CONFIG_DIR, CONFIG_NAME);
		if (!cfgFile.exists()) generateConfig(cfgFile);

		String json = Easy.stringFromFile(cfgFile.getAbsolutePath());
		return(Server.Config.fromJson(json));
	}

	// +-------------------+
	// | Config Generation |
	// +-------------------+

	private static void generateConfig(File cfgFile) throws IOException {

		String logPath = getFile(CONFIG_DIR, LOGGING_NAME).getAbsolutePath();
		
		// config.json
		String json = Easy.stringFromSmartyPath(TEMPLATE_CONFIG);
		Server.Config cfg = Server.Config.fromJson(json);

		cfg.RepertoireStore.BasePath = getSubDirectory(DATA_DIR).getAbsolutePath();
		cfg.LoggingConfigPath = logPath;

		json = Utility.getGson().toJson(cfg);
		Easy.stringToFile(cfgFile.getAbsolutePath(), json);

		// logging.properties
		String logDir = getSubDirectory(LOG_DIR).getAbsolutePath();
		if (!logDir.endsWith(File.separator)) logDir += File.separator;
		logDir = logDir.replace("\\", "\\\\"); // for windows
		
		String props = Easy.stringFromSmartyPath(TEMPLATE_LOGGING);
		props = props.replace(LOG_DIR_TOKEN, logDir);
		Easy.stringToFile(logPath, props);
	}
	
	// +---------------------+
	// | Directories & Files |
	// +---------------------+

	private static File getFile(String sub, String file) throws IOException {
		return(new File(getSubDirectory(sub), file));
	}
	
	private static File getSubDirectory(String sub) throws IOException {
		File dir = new File(getVdjDirectory(), sub);
		dir.mkdirs();
		return(dir);
	}

	private static File getVdjDirectory() throws IOException {
		File dir = new File(System.getProperty("user.home"), VDJ_DIR);
		dir.mkdirs();
		return(dir);
	}

	// +-----------+
	// | Constants |
	// +-----------+

	private static final String CONFIG_NAME = "config.json";
	private static final String LOGGING_NAME = "logging.properties";
	
	private static final String VDJ_DIR = "vdj";
	private static final String CONFIG_DIR = "config";
	private static final String LOG_DIR = "log";
	private static final String DATA_DIR = "data";

    private static final String TEMPLATE_CONFIG = "@desktop-config.json";
	private static final String TEMPLATE_LOGGING = "@desktop-logging.properties";

	private static final String LOG_DIR_TOKEN = "[[LOG_DIR]]";
}

