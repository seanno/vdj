/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

package com.shutdownhook.vdj.standalone;

import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.toolbox.WebServer.Request;
import com.shutdownhook.toolbox.WebServer.Response;

import com.shutdownhook.vdj.vdjlib.AdminOps;
import com.shutdownhook.vdj.vdjlib.AzureTokenFactory;
import com.shutdownhook.vdj.vdjlib.AzureTokenFactory.FactoryType;
import com.shutdownhook.vdj.vdjlib.AzureTokenFactory.OnBehalfOfParams;
import com.shutdownhook.vdj.vdjlib.AgateImport;
import com.shutdownhook.vdj.vdjlib.ContextRepertoireStore;
import com.shutdownhook.vdj.vdjlib.Export;
import com.shutdownhook.vdj.vdjlib.RearrangementKey;
import com.shutdownhook.vdj.vdjlib.RearrangementKey.KeyType;
import com.shutdownhook.vdj.vdjlib.RepertoireResult;
import com.shutdownhook.vdj.vdjlib.RepertoireSpec;
import com.shutdownhook.vdj.vdjlib.RepertoireStore;
import com.shutdownhook.vdj.vdjlib.RepertoireStore_Files;
import com.shutdownhook.vdj.vdjlib.RepertoireStore_Blobs;
import com.shutdownhook.vdj.vdjlib.Overlap;
import com.shutdownhook.vdj.vdjlib.Overlap.OverlapMode;
import com.shutdownhook.vdj.vdjlib.Searcher;
import com.shutdownhook.vdj.vdjlib.Tracking;
import com.shutdownhook.vdj.vdjlib.TopXRearrangements;
import com.shutdownhook.vdj.vdjlib.TopXRearrangements.TopXSort;
import com.shutdownhook.vdj.vdjlib.TsvReader;
import com.shutdownhook.vdj.vdjlib.TsvReceiver;
import com.shutdownhook.vdj.vdjlib.TsvReceiver.ReceiveResult;
import com.shutdownhook.vdj.vdjlib.Utility;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;
import com.shutdownhook.vdj.vdjlib.model.Repertoire;

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
		public String AdminUserProp = "AdminUser";
		
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
		public String AgateAuthType;
		public AgateImport.Config Agate;
		public String AgateClientSecretEnvVar = "microsoft-provider-authentication-secret";

		// Export
		public Export.Config Export = new Export.Config();

		// Tracking
		public Tracking.Config Tracking = new Tracking.Config();

		public String ApiBase = "/api";
		public String ContextScope = "contexts";
		public String SearchScope = "search";
		public String OverlapScope = "overlap";
		public String UserScope = "user";
		public String TopXScope = "topx";
		public String AgateScope = "agate";
		public String ExportScope = "export";
		public String AdminScope = "admin";
		public String DxScope = "dxopt";
		public String TrackingScope = "track";

		public String ClientSiteZip = "@clientSite.zip";
		public Boolean StaticPagesRouteHtmlWithoutExtension = false;

		public static Config fromJson(String json) {
			return(Utility.getGson().fromJson(json, Config.class));
		}
	}
	
	public Server(Config cfg) throws Exception {
		
		this.cfg = cfg;
		cfg.WebServer.ReadBodyAsString = false;

		if (cfg.WebServer.StaticPagesDirectory == null) {
			this.cfg.WebServer.StaticPagesZip = cfg.ClientSiteZip;
		}

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
	// POST   /api/contexts/CTX/REP  => save repertoire from body into REP context CTX (QS user, date)
	// DELETE /api/contexts/CTX/REPS => delete repertoire(s) REPS in context CTX

	// GET    /api/search/CTX/REPS   => search REPS in CTX for (QS motif/type/muts/full)

	// GET    /api/overlap/CTX/REPS  => find overlaps in REPS in CTX (QS type/mode)

	// GET    /api/topx/CTX/REPS     => return top COUNT rearrangements from REPS in CTX sorted 
	//                                  descending by SORT (QS sort/count)

	// GET    /api/user              => return user info (for AUTH user)

	// POST   /api/agate                => return list of matching samples (JSON post body; see method)
	// POST   /api/agate/CTX/REP/import => import agate sample into REP context CTX (JSON post body; see method)

	// GET    /api/export/CTX/REP    => export repoertoire (QS fmt)

	// POST   /api/admin/copy        => copy repertoire (JSON post body; see method)

	// GET    /api/dxopt/CTX/REPS    => return potential "dx" rearrangemnets from REPS in CTX
	
	// POST   /api/track/CTX/REPS    => return potential "dx" rearrangemnets from REPS in CTX
	//                                  (JSON post body = array of Rearrangements to track)

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
				else if (info.Scope.equals(cfg.ExportScope)) {

					if (request.Method.equals("GET")) {
						handleExportRequest(info);
						handled = true;
					}
				}
				else if (info.Scope.equals(cfg.AdminScope)) {

					if (isAdmin(info.Request)) {
						handleAdminRequest(info);
						handled = true;
					}
				}
				else if (info.Scope.equals(cfg.DxScope)) {

					if (request.Method.equals("GET")) {
						handleDxOptionsRequest(info);
						handled = true;
					}
				}
				else if (info.Scope.equals(cfg.TrackingScope)) {

					if (request.Method.equals("POST")) {
						handleTrackingRequest(info);
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
		info.Response.setJson(Utility.getGson().toJson(contexts));
	}
	
	// +-----------------+
	// | listRepertoires |
	// +-----------------+
	
	private void listRepertoires(ApiInfo info) throws Exception {
		
		Repertoire[] repertoires =
			store.getContextRepertoires(info.UserId, info.ContextName);

		info.Response.setJson(Utility.getGson().toJson(repertoires));
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
			Repertoire repertoire = findRepertoire(info.getSpec(info.RepertoireName));
			if (repertoire == null) {
				info.Response.Status = 500;
				return;
			}
			
			stm = store.getRepertoireStream(info.getSpec(info.RepertoireName));
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

		String strDate = info.Request.QueryParams.get("date");
		LocalDate date = (Easy.nullOrEmpty(strDate) ? null : LocalDate.parse(strDate));
			
		saveRepertoireInternal(info,
							   info.Request.QueryParams.get("user"),
							   info.Request.BodyStream,
							   null, null, date);
	}
	
	private void saveRepertoireInternal(ApiInfo info,
										String userOverride,
										InputStream stm,
										Long totalCells,
										Double sampleMillis,
										LocalDate effectiveDate) throws Exception {

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

			RepertoireSpec spec = new RepertoireSpec(saveUserId, info.ContextName, info.RepertoireName);
			ReceiveResult result = TsvReceiver.receive(rdr, store, spec, 
													   totalCells, sampleMillis,
													   effectiveDate).get();

			switch (result) {
				case OK:
					Repertoire r = findRepertoire(spec);
					info.Response.setJson(r.toJson());
					break;

				case Exists:
					info.Response.Status = 409; // "409 Conflict"
					break;

				case Error:
					log.warning(String.format("Failed receiving TSV body %s", spec));
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

			boolean ok = store.deleteRepertoire(info.getSpec(info.RepertoireNames[i]));
			responses[i].Result = (ok ? "Deleted OK" : "Error");
		}
		
		info.Response.setJson(Utility.getGson().toJson(responses));
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
		info.Response.setJson(Utility.getGson().toJson(result));
	}

	// +---------+
	// | getUser |
	// +---------+
	
	public static class UserInfo
	{
		public String AuthUserId;
		public String AssumeUserId;
		public Boolean CanUploadToAnyUserId;
		public Boolean AgateEnabled;
		public Boolean AgateUserPassAuth;
		public Boolean IsAdmin;
		public String LogoutPath;
	}

	private void getUser(ApiInfo info) throws Exception {
		
		UserInfo ui = new UserInfo();
		ui.AuthUserId = info.AuthUserId;
		ui.AssumeUserId = info.UserId;
		ui.CanUploadToAnyUserId = getCanUploadToAny(info.Request);
		ui.IsAdmin = isAdmin(info.Request);
		
		ui.AgateEnabled = (cfg.Agate != null && getAgateAuthType() != null);
		ui.AgateUserPassAuth = (FactoryType.UserPass.equals(getAgateAuthType()));

		ui.LogoutPath = cfg.WebServer.LogoutPath;
		
		info.Response.setJson(Utility.getGson().toJson(ui));
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
		params.Repertoires = new String[] {info.RepertoireName };
		params.Sort = sort;
		params.Count = count;

		RepertoireResult result = topx.getAsync(params).get()[0];
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
		public AgateImport.PipelineSample Sample;

		// for query
		public String Query;
	}
	
	private void handleAgateRequest(ApiInfo info) throws Exception {

		if (cfg.Agate == null || getAgateAuthType() == null) {
			throw new Exception("agate request received but not configured");
		}
		
		AgateImport agate = null;

		try {
			String body = new String(info.Request.BodyStream.readAllBytes(), StandardCharsets.UTF_8);
			AgateParams params = Utility.getGson().fromJson(body, AgateParams.class);
			agate = getAgate(params, info);

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

	private AgateImport getAgate(AgateParams params, ApiInfo info) {

		FactoryType authType = getAgateAuthType();
		log.info(String.format("getAgate: %s", authType));
		
		// default
		if (FactoryType.Default.equals(authType)) {
			return(AgateImport.createDefault(cfg.Agate));
		}

		// user pass
		if (FactoryType.UserPass.equals(authType)) {
			return(AgateImport.createUserPass(cfg.Agate, params.User, params.Password));
		}

		// on behalf of
		if (FactoryType.OnBehalfOf.equals(authType)) {
			String secret = System.getenv(cfg.AgateClientSecretEnvVar);
			return(AgateImport.createOnBehalfOf(cfg.Agate, secret, info.Request.User.Token));
		}

		log.severe(String.format("WTF invalid factory type: %s", authType));
		return(null);
	}

	private void getAgateSamples(ApiInfo info, AgateParams params, AgateImport agate) throws Exception {

		List<AgateImport.PipelineSample> samples = agate.listSamplesPipelineAsync(params.SearchString).get();
		if (samples == null) throw new Exception("failed listing agate samples");
		info.Response.setJson(Utility.getGson().toJson(samples));
	}
	
	private void importAgateSample(ApiInfo info, AgateParams params, AgateImport agate) throws Exception {

		InputStream stm = null;

		try {
			stm = agate.getTsvStreamAsync(params.Sample.TsvPath).get();
			if (stm == null) throw new Exception("failed getting agate tsv stream");
			saveRepertoireInternal(info, params.SaveUser, stm, null, null, params.Sample.Date);
		}
		finally {
			if (stm != null) stm.close();
		}
	}

	// +---------------------+
	// | handleExportRequest |
	// +---------------------+
	
	private void handleExportRequest(ApiInfo info) throws Exception {

		String formatStr = info.Request.QueryParams.get("fmt");
		Export.Format format = Export.Format.valueOf(formatStr);

		Export.Params params = new Export.Params();
		params.CRS = new ContextRepertoireStore(store, info.UserId, info.ContextName);
		params.Repertoire = info.RepertoireName;
		params.Format = format;

		File exportFile = null;

		try {

			exportFile = new Export(cfg.Export).exportAsync(params).get();
			if (exportFile == null) throw new Exception("failed exporting repertoire");

			String name = Easy.urlEncode(info.RepertoireName) + "." + format.getExtension();

			info.Response.Status = 200;
			info.Response.addHeader("Content-Disposition", "attachment; filename=" + name);
			info.Response.ContentType = "application/octet-stream";
			info.Response.BodyFile = exportFile;
			info.Response.DeleteBodyFile = true;
		}
		catch (Exception e) {

			if (exportFile != null) {
				try { exportFile.delete(); }
				catch (Exception de) { /* eat it */ }
			}

			throw e;
		}
	}

	// +----------+
	// | Tracking |
	// +----------+

	private void handleDxOptionsRequest(ApiInfo info) throws Exception {

		Tracking track = new Tracking(cfg.Tracking);
		ContextRepertoireStore crs = new ContextRepertoireStore(store, info.UserId, info.ContextName);

		List<Tracking.RepertoireResultSelections> options =
			track.getDxOptionsAsync(crs, info.RepertoireNames).get();

		info.Response.setJson(Utility.getGson().toJson(options));
	}

	private void handleTrackingRequest(ApiInfo info) throws Exception {
		
		Tracking.Params params = new Tracking.Params();
		params.CRS = new ContextRepertoireStore(store, info.UserId, info.ContextName);
		params.Repertoires = info.RepertoireNames;
		
		String body = new String(info.Request.BodyStream.readAllBytes(), StandardCharsets.UTF_8);
		params.Targets = Utility.getGson().fromJson(body, Rearrangement[].class);

		Tracking track = new Tracking(cfg.Tracking);
		Tracking.Results results = track.trackAsync(params).get();

		info.Response.setJson(Utility.getGson().toJson(results));
	}

	// +--------------------+
	// | handleAdminRequest |
	// +--------------------+

	private void handleAdminRequest(ApiInfo info) throws Exception {

		String body = (info.Request.BodyStream == null ? null
					   :new String(info.Request.BodyStream.readAllBytes(), StandardCharsets.UTF_8));

		switch (info.ContextName) { 
			case "copy": adminCopyRepertoire(info, body); break;
			case "move": adminMoveRepertoire(info, body); break;
			case "obo": adminVerifyBlobOBO(info, body); break;
			case "deets": adminGetRequestDetails(info); break;
			case "aquery": adminQueryAgate(info, body); break;
			default: info.Response.Status = 500; break;
		}
	}

	// adminCopy
	private void adminCopyRepertoire(ApiInfo info, String body) throws Exception {

		AdminOps.MoveCopyParams params = Utility.getGson().fromJson(body, AdminOps.MoveCopyParams.class);

		ReceiveResult result = AdminOps.copyRepertoireAsync(store, params).get();

		if (result == ReceiveResult.OK) {
			Repertoire r = findRepertoire(params.To);
			info.Response.setJson(r.toJson());
			return;
		}

		info.Response.Status = 500;
	}

	// adminMove
	private void adminMoveRepertoire(ApiInfo info, String body) throws Exception {
		
		AdminOps.MoveCopyParams params = Utility.getGson().fromJson(body, AdminOps.MoveCopyParams.class);

		boolean success = AdminOps.moveRepertoireAsync(store, params).get();

		if (success) {
			info.Response.setJson("{ \"result\": \"OK\" }");
			return;
		}
		
		info.Response.Status = 500;
	}
	
	// request details
	private void adminGetRequestDetails(ApiInfo info) throws Exception {
		info.Response.setJson(Admin.getRequestDetails(info.Request));
	}

	// adminOBO
	private void adminVerifyBlobOBO(ApiInfo info, String body) throws Exception {
		boolean ok = Admin.verifyBlobOBOAccess(body, info.Request, cfg.AgateClientSecretEnvVar);
		info.Response.setJson(String.format("{ \"result\": \"%s\" }", ok ? "OK" : "Error; see logs"));
	}

	// adminQueryAgate
	private void adminQueryAgate(ApiInfo info, String body) throws Exception {
		AgateParams params = Utility.getGson().fromJson(body, AgateParams.class);
		AgateImport agate = getAgate(params, info);
		info.Response.setJson(Utility.getGson().toJson(agate.query(params.Query)));
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

		public RepertoireSpec getSpec(String name) {
			return(new RepertoireSpec(UserId, ContextName, name));
		}
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

	private Repertoire findRepertoire(RepertoireSpec spec) {
		Repertoire[] reps = store.getContextRepertoires(spec.UserId, spec.Context);
		return(Repertoire.find(reps, spec.Name));
	}

	private FactoryType getAgateAuthType() {

		if (cfg.AgateAuthType == null) return(null);

		String val = cfg.AgateAuthType;
		if (val.startsWith("@")) val = System.getenv(val.substring(1));

		return(val == null ? null : FactoryType.valueOf(val));
	}
	
	// +---------------------+
	// | User and Properties |
	// +---------------------+
	
	private String getAuthUser(Request request) throws Exception {
		String user = request.User.Email;
		if (Easy.nullOrEmpty(user)) user = request.User.Id;
		if (Easy.nullOrEmpty(user)) throw new Exception("missing auth email or id");

		if (user.indexOf("@") == -1 && request.User.Properties != null) {
			String preferred = request.User.Properties.get("preferred_username");
			if (!Easy.nullOrEmpty(preferred) && preferred.indexOf("@") != -1) {
				user = preferred;
			}
		}
		
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
				return(name.substring(cfg.AssumeUserIdProp.length() + 1));
			}
		}

		return(defaultVal);
	}

	public boolean getCanUploadToAny(Request request) {
		String val = getUserProp(request, cfg.CanUploadToAnyUserIdProp, null);
		return(Easy.nullOrEmpty(val) ? false : Boolean.parseBoolean(val));
	}

	public boolean isAdmin(Request request) {
		String val = getUserProp(request, cfg.AdminUserProp, null);
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

	private RepertoireStore store;

	private Searcher searcher;
	private TopXRearrangements topx;
	private Overlap overlap;

	private final static Logger log = Logger.getLogger(Server.class.getName());
}
