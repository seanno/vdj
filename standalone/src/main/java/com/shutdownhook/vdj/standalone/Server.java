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
import com.shutdownhook.vdj.vdjlib.Searcher;
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

		public Boolean DefaultAAForSearch = false;
		public Integer DefaultMutsForSearch = 0;
		public Integer MinimumNucleotideLengthForSearch = 10;
		public Integer MaximumNucleotideMutationsForSearch = 5;
		public Integer MinimumAALengthForSearch = 5;
		public Integer MaximumAAMutationsForSearch = 2;
		
		public String ApiBase = "/api";
		public String ContextScope = "contexts";
		public String SearchScope = "search";

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

		registerApi();
	}

	// +----------------+
	// | Server Control |
	// +----------------+

	public void start() { server.start(); }
	public void runSync() throws Exception { server.runSync(); }
	public void close() { server.close(); }

	// +-------------+
	// | registerApi |
	// +-------------+

	// GET    /api/contexts         => list contexts
	// GET    /api/contexts/CTX     => return repertoires in context CTX
	// GET    /api/contexts/CTX/REP => return repertoire REP in context CTX (QS start/count)
	// POST   /api/contexts/CTX/REP => save repertoire from body into REP context CTX
	// DELETE /api/contexts/CTX/REP => delete repertoire REP in context CTX

	// GET    /api/search/CTX/REPS  => search REPS in CTX for (QS motif/isaa/muts)

	private void registerApi() throws Exception {

		server.registerHandler(cfg.ApiBase, new WebServer.Handler() {
			public void handle(Request request, Response response) throws Exception {

				ApiInfo info = getApiInfo(request, response);

				boolean handled = false;
				
				if (info.Scope.equals(cfg.ContextScope)) {
					
					switch (request.Method) {
						case "GET":
							handled = true;

							if (info.ContextName != null && info.RepertoireName != null) {
								// get contents of repertoire
								getRepertoire(info);
							}
							else if (info.ContextName != null) {
								// list all repertoires in context
								listRepertoires(info);
							}
							else {
								// list all contexts for user
								listContexts(info);
							}
							break;

						case "POST":
							if (info.ContextName != null && info.RepertoireName != null) {
								// save repertoire
								handled = true;
								saveRepertoire(info);
							}
							break;

						case "DELETE":
							if (info.ContextName != null && info.RepertoireName != null) {
								// delete repertoire
								handled = true;
								deleteRepertoire(info);
							}
							break;
					}
				}
				else if (info.Scope.equals(cfg.SearchScope)) {

					if (request.Method.equals("GET") &&
						info.ContextName != null &&
						info.RepertoireNames != null) {

						// search within repertoire
						searchRepertoires(info);
						handled = true;
					}
					
				}

				if (!handled) {
					response.Status = 500;
					response.Body = "Malformed request";
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

		log.info(String.format("GR: %d %d / %s / %s", start, count,
							   info.ContextName, info.RepertoireName));
		
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
		info.Response.setText("NYI");
	}

	// +-------------------+
	// | searchRepertoires |
	// +-------------------+
	
	private void searchRepertoires(ApiInfo info) throws Exception {
		
		String strIsAA = info.Request.QueryParams.get("isaa");
		boolean isAA = (Easy.nullOrEmpty(strIsAA) ? cfg.DefaultAAForSearch : Boolean.parseBoolean(strIsAA));
		
		String strMuts = info.Request.QueryParams.get("muts");
		int muts = (Easy.nullOrEmpty(strMuts) ? cfg.DefaultMutsForSearch : Integer.parseInt(strMuts));
		
		String motif = info.Request.QueryParams.get("motif");
		if (Easy.nullOrEmpty(motif)) throw new IllegalArgumentException("motif required");
		motif = motif.toUpperCase();
		
		if ((!isAA && (motif.length() < cfg.MinimumNucleotideLengthForSearch)) ||
			(!isAA && (muts < 0 || muts > cfg.MaximumNucleotideMutationsForSearch)) ||
			(isAA && (motif.length() < cfg.MinimumAALengthForSearch)) ||
			(isAA && (muts < 0 || muts > cfg.MaximumAAMutationsForSearch))) {
			
			throw new IllegalArgumentException("Motif or Mutations out of configured limits");
		}

		Searcher.SearchParams params = new Searcher.SearchParams();
		params.Store = store;
		params.UserId = info.UserId;
		params.Context = info.ContextName;
		params.Repertoires = info.RepertoireNames;
		params.Motif = motif;
		params.MotifIsAA = isAA;
		params.AllowedMutations = muts;

		Searcher.RepertoireResult[] results = Searcher.searchAsync(params).get();
		info.Response.setJson(Searcher.resultsToJson(results));
	}

	// +---------+
	// | ApiInfo |
	// +---------+

	static class ApiInfo
	{
		public Request Request;
		public Response Response;
		public String UserId;
		public String Scope;
		public String ContextName;
		public String RepertoireName;
		public String[] RepertoireNames;
	}
	
	private ApiInfo getApiInfo(Request request, Response response) throws Exception {
		
		ApiInfo info = new ApiInfo();
		info.Request = request;
		info.Response = response;
		info.UserId = getAuthUser(request);

		// trim off the common start of the path and any query string
		int ichMac = request.Path.indexOf("?");
		if (ichMac == -1) ichMac = request.Path.length();
		String[] components = request.Path.substring(cfg.ApiBase.length() + 1, ichMac).split("/");

		info.Scope = Easy.urlDecode(components[0]);
		
		if (components.length > 1) {
			String contextName = Easy.urlDecode(components[1]);
			if (!contextName.isEmpty()) info.ContextName = contextName;
		}
		
		if (components.length > 2) {
			String repertoireNamesCsv = Easy.urlDecode(components[2]);
			if (!repertoireNamesCsv.isEmpty()) {
				info.RepertoireNames = repertoireNamesCsv.split(",");
				if (info.RepertoireNames.length == 1) info.RepertoireName = info.RepertoireNames[0];
			}
		}

		return(info);
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
