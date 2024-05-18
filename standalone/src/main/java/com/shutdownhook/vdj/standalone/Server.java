/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.vdj.standalone;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.Request;
import com.shutdownhook.toolbox.WebServer.Response;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;
import com.shutdownhook.vdj.vdjlib.RepertoireStore;
import com.shutdownhook.vdj.vdjlib.RepertoireStore_Files;
import com.shutdownhook.vdj.vdjlib.TsvReader;
import com.shutdownhook.vdj.vdjlib.TsvReceiver;

public class Server implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public WebServer.Config WebServer = new WebServer.Config();

		// for now just hardcode in a RepertoireStore_Files
		public RepertoireStore_Files.Config RepertoireStore;

		public String LoggingConfigPath = "@logging.properties";

		public Integer DefaultGetCount = 50;
		public Integer MaxGetCount = 250;
		
		public String ContextsUrl = "/api/contexts";

		public String ClientSiteZip = "@clientSite.zip";
		public Boolean StaticPagesRouteHtmlWithoutExtension = false;

		public static Config fromJson(String json) {
			return(new Gson().fromJson(json, Config.class));
		}
	}
	
	public Server(Config cfg) throws Exception {
		
		this.cfg = cfg;
		cfg.WebServer.ReadBodyAsString = false;

		if (cfg.WebServer.StaticPagesDirectory == null) {
			this.cfg.WebServer.StaticPagesZip = cfg.ClientSiteZip;
		}

		this.gson = new Gson();

		store = new RepertoireStore_Files(cfg.RepertoireStore);
		
		setupWebServer();
	}
	
	private void setupWebServer() throws Exception {

		server = WebServer.create(cfg.WebServer);

		registerContexts();
	}

	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); }

	// +------------------+
	// | registerContexts |
	// +------------------+

	// GET    /api/contexts         => list contexts
	// GET    /api/contexts/CTX     => return repertoires in context CTX
	// GET    /api/contexts/CTX/REP => return repertoire REP in context CTX
	// POST   /api/contexts/CTX/REP => save repertoire from body into REP context CTX
	// DELETE /api/contexts/CTX/REP => delete repertoire REP in context CTX

	static class ApiInfo
	{
		public Request Request;
		public Response Response;
		public String UserId;
		public String ContextName;
		public String RepertoireName;
	}
	
	private void registerContexts() throws Exception {

		final int baseCount = cfg.ContextsUrl.split("/").length;
			
		server.registerHandler(cfg.ContextsUrl, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				ApiInfo info = new ApiInfo();
				info.Request = request;
				info.Response = response;
				info.UserId = getAuthUser(request);
				
				String[] components = request.Path.split("/");
				if (components.length > baseCount) {
					
					String contextName = Easy.urlDecode(components[baseCount]);
					if (!contextName.equals("")) info.ContextName = contextName;
					
					if (components.length > baseCount + 1) {
						String repertoireName = Easy.urlDecode(components[baseCount+1]);
						if (!repertoireName.equals("")) info.RepertoireName = repertoireName;
					}
				}

				switch (request.Method) {
					case "GET":
						if (info.ContextName == null || info.RepertoireName == null) {
							listContexts(info);
						}
						else if (info.RepertoireName == null) {
							listRepertoires(info);
						}
						else {
							getRepertoire(info);
						}
						break;

					case "POST":
						saveRepertoire(info);
						break;

					case "DELETE":
						deleteRepertoire(info);
						break;

					default:
						response.Status = 500;
						break;
				}

			}
		});
	}

	// +--------------+
	// | listContexts |
	// +--------------+

	private void listContexts(ApiInfo info) throws Exception {
		String[] contexts = store.getUserContexts(info.UserId);
		info.Response.setJson(gson.toJson(contexts));
	}
	
	// +-----------------+
	// | listRepertoires |
	// +-----------------+
	
	private void listRepertoires(ApiInfo info) throws Exception {
		
		Repertoire[] repertoires =
			store.getContextRepertoires(info.UserId, info.ContextName);

		info.Response.setJson(gson.toJson(repertoires));
	}

	// +---------------+
	// | getRepertoire |
	// +---------------+

	// NOTE this does NOT stream the repertoire back to the user, and instead
	// just loads it all into memory. My bet is that this is fine given the small
	// max batch size we're allowing, but we can revisit if needed.
	
	private void getRepertoire(ApiInfo info) throws Exception {

		// params
		
		String strStart = info.Request.QueryParams.get("start");
		String strCount = info.Request.QueryParams.get("count");

		int start = Easy.nullOrEmpty(strStart)
			? 0: Integer.parseInt(strStart);

		int count = Easy.nullOrEmpty(strCount)
			? cfg.DefaultGetCount : Integer.parseInt(strCount);

		if (count > cfg.MaxGetCount) count = cfg.MaxGetCount;

		// fetch

		InputStream stm = null;
		InputStreamReader rdr = null;
		TsvReader tsv = null;

		try {
			Repertoire repertoire = findRepertoire(info.UserId, info.ContextName, info.RepertoireName);
			if (repertoire == null) {
				info.Response.Status = 500;
				return;
			}
			
			stm = store.getRepertoireStream(info.UserId, info.ContextName, info.RepertoireName);
			rdr = new InputStreamReader(stm);
			tsv = new TsvReader(rdr, start);

			List<Rearrangement> rearrangements = tsv.readNextBatchAsync(count).get();
			info.Response.setJson(Rearrangement.toJsonArray(repertoire, rearrangements));
		}
		finally {
			if (tsv != null) tsv.close();
			if (rdr != null) rdr.close();
			if (stm != null) stm.close();
		}

	}

	// +----------------+
	// | saveRepertoire |
	// +----------------+

	private void saveRepertoire(ApiInfo info) throws Exception {

		InputStreamReader rdr = null;

		try {
			rdr = new InputStreamReader(info.Request.BodyStream);

			Boolean success = TsvReceiver.receive(rdr, store,
												  info.UserId, info.ContextName,
												  info.RepertoireName).get();

			if (!success) {
				log.warning(String.format("Failed receiving TSV body %s/%s/%s",
										  info.UserId, info.ContextName, info.RepertoireName));
				
				info.Response.Status = 500;
			}
			else {
				Repertoire r = findRepertoire(info.UserId, info.ContextName, info.RepertoireName);
				info.Response.setJson(r.toJson());
			}
		}
		finally {
			if (rdr != null) rdr.close();
		}
	}

	// +------------------+
	// | deleteRepertoire |
	// +------------------+
	
	private void deleteRepertoire(ApiInfo info) throws Exception {
		// nyi
	}

	// +---------+
	// | Helpers |
	// +---------+

	private Repertoire findRepertoire(String userId, String context, String rep) {

		for (Repertoire repertoire : store.getContextRepertoires(userId, context)) {
			if (repertoire.Name.equals(rep)) return(repertoire);
		}

		return(null);
	}
	
	private static String getAuthUser(Request request) throws Exception {
		String user = request.User.Email;
		if (Easy.nullOrEmpty(user)) user = request.User.Id;
		if (Easy.nullOrEmpty(user)) throw new Exception("missing auth email or id");
		return(user);
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebServer server;
	private Gson gson;

	private RepertoireStore store;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
