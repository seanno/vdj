/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.vdj.standalone;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.gson.Gson;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.Request;
import com.shutdownhook.toolbox.WebServer.Response;

import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;
import com.shutdownhook.vdj.vdjlib.AgateImport;
import com.shutdownhook.vdj.vdjlib.ContextRepertoireStore;
import com.shutdownhook.vdj.vdjlib.RearrangementKey;
import com.shutdownhook.vdj.vdjlib.RearrangementKey.KeyType;
import com.shutdownhook.vdj.vdjlib.RepertoireResult;
import com.shutdownhook.vdj.vdjlib.RepertoireStore;
import com.shutdownhook.vdj.vdjlib.RepertoireStore_Files;
import com.shutdownhook.vdj.vdjlib.RepertoireStore_Blobs;
import com.shutdownhook.vdj.vdjlib.Overlap;
import com.shutdownhook.vdj.vdjlib.Overlap.OverlapMode;
import com.shutdownhook.vdj.vdjlib.Searcher;
import com.shutdownhook.vdj.vdjlib.TopXRearrangements;
import com.shutdownhook.vdj.vdjlib.TopXRearrangements.TopXSort;
import com.shutdownhook.vdj.vdjlib.TsvReader;
import com.shutdownhook.vdj.vdjlib.TsvReceiver;
import com.shutdownhook.vdj.vdjlib.TsvReceiver.ReceiveResult;

public class Server implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public WebServer.Config WebServer = new WebServer.Config();

		// Only one of these should be provided. Sorry for the name
		// inconsistency, I didn't want to go back and change a bunch
		// of existing config files
		public RepertoireStore_Files.Config RepertoireStore;
		public RepertoireStore_Blobs.Config RepertoireStoreBlobs;

		public String LoggingConfigPath = "@logging.properties";

		public String CanUploadToAnyUserIdProp = "CanUploadToAnyUserId";
		public String AssumeUserIdProp = "AssumeUserId";
		
		// Get
		public Integer DefaultGetCount = 50;
		public Integer MaxGetCount = 250;

		// Search
		public Searcher.Config Searcher = new Searcher.Config();
		public KeyType DefaultSearchType = KeyType.Rearrangement;
		public Integer DefaultSearchMuts = 0;
		public Boolean DefaultSearchFullMatch = false;

		// Overlap
		public Overlap.Config Overlap = new Overlap.Config();
		public OverlapMode DefaultOverlapMode = OverlapMode.Standard;
		public KeyType DefaultOverlapType = KeyType.CDR3;

		// TopX
		public TopXRearrangements.Config TopX = new TopXRearrangements.Config();
		public Integer DefaultTopXCount = 100;
		public TopXSort DefaultTopXSort = TopXSort.FractionOfCells;

		// Agate
		public Boolean AgateUserPassAuth = false;
		public Boolean AgateOnBehalfOfAuth = false;
		public AgateImport.Config Agate;
		
		public String ApiBase = "/api";
		public String ContextScope = "contexts";
		public String SearchScope = "search";
		public String OverlapScope = "overlap";
		public String UserScope = "user";
		public String TopXScope = "topx";
		public String AgateScope = "agate";

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

		store = createStore();

		searcher = new Searcher(cfg.Searcher);
		topx = new TopXRearrangements(cfg.TopX);
		overlap = new Overlap(cfg.Overlap);
		
		setupWebServer();
	}
	
	private void setupWebServer() throws Exception {

		server = WebServer.create(cfg.WebServer);

		registerApi();
	}

	private RepertoireStore createStore() throws Exception {

		if (cfg.RepertoireStore != null) {
			return(new RepertoireStore_Files(cfg.RepertoireStore));
		}

		if (cfg.RepertoireStoreBlobs != null) {
			return(new RepertoireStore_Blobs(cfg.RepertoireStoreBlobs));
		}

		throw new Exception("Missing RepertoireStore configuration");
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

	// GET    /api/contexts          => list contexts
	// GET    /api/contexts/CTX      => return repertoires in context CTX
	// GET    /api/contexts/CTX/REP  => return repertoire REP in context CTX (QS start/count)
	// POST   /api/contexts/CTX/REP  => save repertoire from body into REP context CTX (QS user)
	// DELETE /api/contexts/CTX/REPS => delete repertoire(s) REPS in context CTX

	// GET    /api/search/CTX/REPS   => search REPS in CTX for (QS motif/type/muts/full)

	// GET    /api/overlap/CTX/REPS  => find overlaps in REPS in CTX (QS type/mode)

	// GET    /api/topx/CTX/REP      => return top COUNT rearrangements from REP in CTX sorted 
	//                                  descending by SORT (QS sort/count)

	// GET    /api/user              => return user info (for AUTH user)

	// POST   /api/agate                => return list of matching samples (JSON post body; see method)
	// POST   /api/agate/CTX/REP/import => import agate sample into REP context CTX (JSON post body; see method)

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
							if (info.ContextName != null && info.RepertoireNames != null) {
								// delete repertoires
								handled = true;
								deleteRepertoires(info);
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
				else if (info.Scope.equals(cfg.OverlapScope)) {

					if (request.Method.equals("GET") &&
						info.ContextName != null &&
						info.RepertoireNames != null) {

						// compute overlaps across repertoires
						overlapRepertoires(info);
						handled = true;
					}
					
				}
				else if (info.Scope.equals(cfg.UserScope)) {

					if (request.Method.equals("GET")) {
						
						// user info
						getUser(info);
						handled = true;
					}
					
				}
				else if (info.Scope.equals(cfg.TopXScope)) {

					if (request.Method.equals("GET")) {

						// get topx rearrangements
						getTopX(info);
						handled = true;
					}
				}
				else if (info.Scope.equals(cfg.AgateScope)) {

					if (request.Method.equals("POST")) {
						handleAgateRequest(info);
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

		saveRepertoireInternal(info,
							   info.Request.QueryParams.get("user"),
							   info.Request.BodyStream,
							   null, null);
	}
	
	private void saveRepertoireInternal(ApiInfo info,
										String userOverride,
										InputStream stm,
										Long totalCells,
										Double sampleMillis) throws Exception {

		String saveUserId = userOverride;
		
		if (Easy.nullOrEmpty(userOverride)) {
			// no override provided, use effective user id
			saveUserId = info.UserId;
		}
		else {
			// authorized? 
			if (!getCanUploadToAny(info.Request)) {

				log.warning(String.format("User %s tried to upload to %s without auth!",
										  info.AuthUserId, saveUserId));
				info.Response.Status = 401;
				return;
			}

			log.info(String.format("User %s uploading to %s", info.AuthUserId, saveUserId));
		}

		InputStreamReader rdr = null;

		try {
			rdr = new InputStreamReader(stm);

			
			ReceiveResult result = TsvReceiver.receive(rdr, store,
													   saveUserId, info.ContextName,
													   info.RepertoireName,
													   totalCells, sampleMillis).get();

			switch (result) {
				case OK:
					Repertoire r = findRepertoire(saveUserId, info.ContextName, info.RepertoireName);
					info.Response.setJson(r.toJson());
					break;

				case Exists:
					info.Response.Status = 409; // "409 Conflict"
					break;

				case Error:
					log.warning(String.format("Failed receiving TSV body %s/%s/%s",
											  saveUserId, info.ContextName, info.RepertoireName));
					info.Response.Status = 500;
					break;
			}
		}
		finally {
			if (rdr != null) rdr.close();
		}
	}

	// +-------------------+
	// | deleteRepertoires |
	// +-------------------+

	static public class DeleteResponse
	{
		public String Name;
		public String Result;
	}
	
	private void deleteRepertoires(ApiInfo info) throws Exception {

		DeleteResponse[] responses = new DeleteResponse[info.RepertoireNames.length];

		for (int i = 0; i < info.RepertoireNames.length; ++i) {
			
			responses[i] = new DeleteResponse();
			responses[i].Name = info.RepertoireNames[i];

			boolean ok = store.deleteRepertoire(info.UserId, info.ContextName, info.RepertoireNames[i]);
			responses[i].Result = (ok ? "Deleted OK" : "Error");
		}
		
		info.Response.setJson(gson.toJson(responses));
	}

	// +-------------------+
	// | searchRepertoires |
	// +-------------------+

	private void searchRepertoires(ApiInfo info) throws Exception {

		String typeStr = info.Request.QueryParams.get("type");
		KeyType keyType = (Easy.nullOrEmpty(typeStr) ? cfg.DefaultSearchType : KeyType.valueOf(typeStr));
		
		String strMuts = info.Request.QueryParams.get("muts");
		int muts = (Easy.nullOrEmpty(strMuts) ? cfg.DefaultSearchMuts : Integer.parseInt(strMuts));
		
		String strFull = info.Request.QueryParams.get("full");
		Boolean full = (Easy.nullOrEmpty(strFull) ? cfg.DefaultSearchFullMatch : Boolean.parseBoolean(strFull));
		
		String motif = info.Request.QueryParams.get("motif");
		if (Easy.nullOrEmpty(motif)) throw new IllegalArgumentException("motif required");
		motif = motif.toUpperCase();

		Searcher.Params params = new Searcher.Params();
		params.CRS = new ContextRepertoireStore(store, info.UserId, info.ContextName);
		params.Repertoires = info.RepertoireNames;
		params.Motif = motif;
		params.Extractor = RearrangementKey.getExtractor(keyType);
		params.Matcher = RearrangementKey.getMatcher(keyType, muts, full);

		RepertoireResult[] results = searcher.searchAsync(params).get();
		info.Response.setJson(RepertoireResult.resultsToJson(results));
	}

	// +--------------------+
	// | overlapRepertoires |
	// +--------------------+
	
	private void overlapRepertoires(ApiInfo info) throws Exception {
		
		String typeStr = info.Request.QueryParams.get("type");
		KeyType keyType = (Easy.nullOrEmpty(typeStr) ?
						   cfg.DefaultOverlapType : KeyType.valueOf(typeStr));

		String modeStr = info.Request.QueryParams.get("mode");
		OverlapMode mode = (Easy.nullOrEmpty(modeStr) ?
									cfg.DefaultOverlapMode : OverlapMode.valueOf(modeStr));

		Overlap.Params params = new Overlap.Params();
		params.CRS = new ContextRepertoireStore(store, info.UserId, info.ContextName);
		params.RepertoireNames = info.RepertoireNames;
		params.Extractor = RearrangementKey.getExtractor(keyType);
		params.Mode = mode;
		
		Overlap.OverlapResult result = overlap.overlapAsync(params).get();
		info.Response.setJson(gson.toJson(result));
	}

	// +---------+
	// | getUser |
	// +---------+
	
	public static class UserInfo
	{
		public String AssumeUserId;
		public Boolean CanUploadToAnyUserId;
		public Boolean AgateEnabled;
		public Boolean AgateUserPassAuth;
	}

	private void getUser(ApiInfo info) throws Exception {
		
		UserInfo ui = new UserInfo();
		ui.AssumeUserId = info.UserId;
		ui.CanUploadToAnyUserId = getCanUploadToAny(info.Request);

		ui.AgateEnabled = (cfg.Agate != null);
		ui.AgateUserPassAuth = (cfg.Agate != null && cfg.AgateUserPassAuth);
							
		info.Response.setJson(gson.toJson(ui));
	}

	// +---------+
	// | getTopX |
	// +---------+
	
	private void getTopX(ApiInfo info) throws Exception {
		
		String strCount = info.Request.QueryParams.get("count");
		int count = (Easy.nullOrEmpty(strCount) ? cfg.DefaultTopXCount : Integer.parseInt(strCount));

		String strSort = info.Request.QueryParams.get("sort");
		TopXSort sort = (Easy.nullOrEmpty(strSort) ? cfg.DefaultTopXSort : TopXSort.valueOf(strSort));

		TopXRearrangements.Params params = new TopXRearrangements.Params();
		params.CRS = new ContextRepertoireStore(store, info.UserId, info.ContextName);
		params.Repertoire = info.RepertoireName;
		params.Sort = sort;
		params.Count = count;

		RepertoireResult result = topx.getAsync(params).get();
		info.Response.setJson(result.toJson());
	}

	// +--------------------+
	// | handleAgateRequest |
	// +--------------------+

	public static class AgateParams
	{
		public String User;
		public String Password;

		// for searching
		public String SearchString;

		// for importing
		public String SaveUser;
		public AgateImport.Sample Sample;
	}
	
	private void handleAgateRequest(ApiInfo info) throws Exception {

		if (cfg.Agate == null) throw new Exception("agate request received but not configured");
		
		AgateImport agate = null;

		try {
			String body = new String(info.Request.BodyStream.readAllBytes(), StandardCharsets.UTF_8);
			AgateParams params = gson.fromJson(body, AgateParams.class);
			
			String user = (cfg.AgateUserPassAuth ? params.User : null);
			String pass = (cfg.AgateUserPassAuth ? params.Password : null);
			agate = new AgateImport(cfg.Agate, user, pass);

			if (info.ContextName == null) {
				getAgateSamples(info, params, agate);
			}
			else {
				importAgateSample(info, params, agate);
			}
		}
		finally {
			if (agate != null) agate.close();
		}
	}

	private void getAgateSamples(ApiInfo info, AgateParams params, AgateImport agate) throws Exception {

		List<AgateImport.Sample> samples = agate.listSamplesAsync(params.SearchString).get();
		if (samples == null) throw new Exception("failed listing agate samples");
		info.Response.setJson(gson.toJson(samples));
	}
	
	private void importAgateSample(ApiInfo info, AgateParams params, AgateImport agate) throws Exception {

		InputStream stm = null;

		try {
			stm = agate.getTsvStreamAsync(params.Sample.TsvPath).get();
			if (stm == null) throw new Exception("failed getting agate tsv stream");

			saveRepertoireInternal(info, params.SaveUser, stm,
								   params.Sample.TotalCells,
								   params.Sample.TotalMilliliters);
			
		}
		finally {
			if (stm != null) stm.close();
		}
	}

	// +---------+
	// | ApiInfo |
	// +---------+

	static class ApiInfo
	{
		public Request Request;
		public Response Response;
		
		public String AuthUserId; // the actual logged-in user
		public String UserId; // the "effective" logged-in user
		
		public String Scope;
		public String ContextName;
		public String RepertoireName;
		public String[] RepertoireNames;
	}
	
	private ApiInfo getApiInfo(Request request, Response response) throws Exception {
		
		ApiInfo info = new ApiInfo();
		info.Request = request;
		info.Response = response;
		
		info.AuthUserId = getAuthUser(request);
		info.UserId = getAssumeUserId(request);
		
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
	
	// +---------------------+
	// | User and Properties |
	// +---------------------+
	
	private String getAuthUser(Request request) throws Exception {
		String user = request.User.Email;
		if (Easy.nullOrEmpty(user)) user = request.User.Id;
		if (Easy.nullOrEmpty(user)) throw new Exception("missing auth email or id");
		return(user);
	}
	
	public String getAssumeUserId(Request request) throws Exception {
		
		String defaultVal = getAuthUser(request);
		if (request.User.Properties == null) return(defaultVal);

		String val = request.User.Properties.get(cfg.AssumeUserIdProp);
		if (val != null) return(val);

		// This is for XMS auth where we are using roles to hold properties.
		// Properties just "exist" or don't, so we make the actual assumed user
		// id be part of the property name itself; e.g. AssumeUserId_sean@thenolans.com.
		for (String name : request.User.Properties.keySet()) {
			if (name.startsWith(cfg.AssumeUserIdProp + "_")) {
				int cchPrefix = cfg.AssumeUserIdProp.length() + 1;
				return(request.User.Properties.get(name).substring(cchPrefix));
			}
		}

		return(defaultVal);
	}

	public boolean getCanUploadToAny(Request request) {
		String val = getUserProp(request, cfg.CanUploadToAnyUserIdProp, null);
		return(Easy.nullOrEmpty(val) ? false : Boolean.parseBoolean(val));
	}
	
	private String getUserProp(Request request, String name, String defaultVal) {
		if (request.User.Properties == null) return(defaultVal);
		String val = request.User.Properties.get(name);
		return(Easy.nullOrEmpty(val) ? defaultVal : val);
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private WebServer server;
	private Gson gson;

	private RepertoireStore store;

	private Searcher searcher;
	private TopXRearrangements topx;
	private Overlap overlap;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
