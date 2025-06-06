//
// AGATEIMPORT.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.Closeable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipException;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

import com.shutdownhook.vdj.vdjlib.AzureTokenFactory.DefaultParams;
import com.shutdownhook.vdj.vdjlib.AzureTokenFactory.FactoryType;
import com.shutdownhook.vdj.vdjlib.AzureTokenFactory.OnBehalfOfParams;
import com.shutdownhook.vdj.vdjlib.AzureTokenFactory.UserPassParams;

public class AgateImport implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public Integer MinSearchLength = 5;
		public Boolean EnablePatientSearchExpansion = true;

		public String ApiResource = "https://adaptiveagateuserfunctions.azurewebsites.net/user_impersonation";
		public String ApiBaseUrl = "https://adaptiveagateapifunctions.azurewebsites.net/api";
		public String StorageResource = "https://storage.azure.com/.default";
		public String StorageVersion = "2017-11-09";
		public Integer TimeoutMillis = (5 * 60 * 1000);

		public String AgateClientId = "fdcf242b-a25b-4b35-aff2-d91d8100225d";
		public String AgateTenantId = "720cf133-4325-491c-b6a9-159d0497fc65";
	}

	public AgateImport(Config cfg, AzureTokenFactory tokenFactory) {
		this.cfg = cfg;
		this.tokenFactory = tokenFactory;
	}

	public void close() {
		// nut-n-honey
	}
	
	// +----------+
	// | Creation |
	// +----------+

	public static AgateImport createDefault(Config cfg) {
		DefaultParams params = new DefaultParams(cfg.AgateTenantId);
		return(new AgateImport(cfg, AzureTokenFactory.create(FactoryType.Default, params)));
	}

	public static AgateImport createUserPass(Config cfg, String user, String pass) {
		UserPassParams params = new UserPassParams(user, pass, cfg.AgateTenantId, cfg.AgateClientId);
		return(new AgateImport(cfg, AzureTokenFactory.create(FactoryType.UserPass, params)));
	}

	public static AgateImport createOnBehalfOf(Config cfg, String secret, String token) {
		OnBehalfOfParams params = new OnBehalfOfParams(secret, token);
		return(new AgateImport(cfg, AzureTokenFactory.create(FactoryType.OnBehalfOf, params)));
	}

	// +-------------------+
	// | getTsvStreamAsync |
	// | getTsvStream      |
	// +-------------------+

	public CompletableFuture<InputStream> getTsvStreamAsync(String tsvPath) {
		return(Exec.runAsync("getTsvStream", new Exec.AsyncOperation() {
			public InputStream execute() throws Exception {
				return(getTsvStream(tsvPath));
			}
		}));
	}
	
	public InputStream getTsvStream(String tsvPath) throws IOException {
		return(getInputStream(tsvPath, cfg.StorageVersion));
	}

	// +--------------------------+
	// | listSamplesPipelineAsync |
	// | listSamplesPipeline      |
	// +--------------------------+

	private static String SAMPLES_FILTER_FMT =
		"contains(Identity/Name,'%s') or " +
		"contains(Project/Name,'%s') or " +
		"contains(Metadata/OrderName,'%s')";

	private static String SAMPLES_URL_FMT =
		"%s/clearinghouse/sequencing_run?$filter=%s";

	public static class PipelineSample
	{
		public String Name;
		public String Project;
		public LocalDate Date;
		public String TsvPath;
	}
	
	public CompletableFuture<List<PipelineSample>> listSamplesPipelineAsync(String search) {
		return(Exec.runAsync("listSamplesPipeline", new Exec.AsyncOperation() {
			public List<PipelineSample> execute() throws Exception {
				return(listSamplesPipeline(search));
			}
		}));
	}
	
	public List<PipelineSample> listSamplesPipeline(String search) throws Exception {

		if (search == null || search.length() < cfg.MinSearchLength) {
			throw new IllegalArgumentException(String.format("search must be at least %d chars",
															 cfg.MinSearchLength));
		}

		log.info(String.format("Fetching Agate (pipeline) samples matching %s", search));

		InputStream stm = null;

		try {
			String searchEsc = Utility.odataEncode(search);
			String query = String.format(SAMPLES_FILTER_FMT, searchEsc, searchEsc, searchEsc);
			String url = String.format(SAMPLES_URL_FMT, cfg.ApiBaseUrl, Utility.urlEncode(query));

			stm = getInputStream(url, cfg.ApiResource);
			String json = Utility.stringFromInputStream(stm);

            List<PipelineSample> samples = new ArrayList<PipelineSample>();

			// TODO 2025-06-06
			//
			// Currently blocked on this work because Agate can't respond to these odata queries
			// without OOM/perf issues. Jeff will work on this in a couple of months when back from
			// vacation.
			//
			// Work is to get this json back and parse it out in to a list of pipeline samples.
			// The front end will also need to be updated to remove the "aquery" admin functionality
			// since we are removing direct sql access (backend of this already done).
			//
			// verify that the combined getInputStream call still works for TSV access as well! And
			// then will need to make sure that the OBO flow works correctly when installed in the
			// Agate tenant; that will have to be coordinated with Jeff but should require no code work?
			//
			// Talked about maybe adding a device auth version so that MFA accounts could work in
			// standalone deployments? (remember to update desktop!) The challege here is that
			// currently there is no async break between asking for a token and making the request
			// ... probably need a new flow where the front end requests device auth and starts the
			// flow and then pushes the token up. will this even work with the app as we have it?
			// (i.e., are we considered a public client?) will have to page all this back into
			// memory before giving it a try.
			
			log.info(json);

			List<PipelineSample> expandedSamples = maybeExpandPatientSearch(search, samples);
			if (expandedSamples != null) {
				log.info("Expanded patient search for " + search);
				return(expandedSamples);
			}

			log.info(String.format("Fetched %d (pipeline) samples matching %s", samples.size(), search));
			return(samples);
		}
		finally {
			Utility.safeClose(stm);
		}
	}

	// this helper turns "D-*" searches that return a samples that matches 
	// the clinical sample format into a patient id search ... so that external users
	// can use a D- number to find all samples for a patient
	private List<PipelineSample> maybeExpandPatientSearch(String search,
														  List<PipelineSample> samples)
		throws Exception {

		// quick bails
		if (samples.size() == 0) return(null);
		if (!cfg.EnablePatientSearchExpansion) return(null);
		if (!search.toLowerCase().startsWith("d-")) return(null);

		// check the sample names ... they all have to start with patient ids
		// AND be the same patient id ... I think there were some clinical tests
		// where we did multiple assays and want to be sure I handle that.
		
		String patientId = null;
		
		for (int i = 0; i < samples.size(); ++i) {
			
			String sampleName = samples.get(i).Name;
			int ichHyphen = sampleName.indexOf("-");
			if (ichHyphen == -1) return(null);

			String thisPatientId = sampleName.substring(0, ichHyphen);
			try { Integer.parseInt(thisPatientId); }
			catch (NumberFormatException e) { return(null); }

			if (patientId != null && !patientId.equals(thisPatientId)) return(null);
			patientId = thisPatientId;
		}

		// ok I believe you
		return(listSamplesPipeline(patientId + "-"));
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private InputStream getInputStream(String path, String resource) throws IOException {

		try {
			return(getInputStreamInternal(path, resource, true));
		}
		catch (ZipException e) {
			log.warning(String.format("Agate says gz/zip but nope; falling back (%s)", path));
			return(getInputStreamInternal(path, resource, false));
		}
	}

	private InputStream getInputStreamInternal(String path, String resource, boolean tryZips) throws IOException {
		
		URL url = new URL(path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		conn.setFollowRedirects(true);
		conn.setConnectTimeout(cfg.TimeoutMillis);
		conn.setReadTimeout(cfg.TimeoutMillis);

		String token = tokenFactory.getToken(resource); 
		conn.setRequestProperty("Authorization", "Bearer " + token);
		conn.setRequestProperty("x-ms-date", msDateString());
		conn.setRequestProperty("x-ms-version", cfg.StorageVersion);
				
		int status = conn.getResponseCode();
		if (status < 200 || status >= 300) {
			
			String errBody = "no body";
			InputStream errStm = null;
			
			try {
				errStm = conn.getInputStream();
				errBody = Utility.stringFromInputStream(errStm);
			}
			catch (Exception e) {
				// eat it
			}
			finally {
				Utility.safeClose(errStm);
			}
			
			log.warning(String.format("Failed Agate req %s: %d %s", url, status, errBody));
			return(null);
		}

		if (tryZips) {
			
			int ichLastDot = path.lastIndexOf(".");
			String ext = (ichLastDot == -1 ? "" : path.substring(ichLastDot + 1));

			if (ext.equalsIgnoreCase("gz")) return(new GZIPInputStream(conn.getInputStream()));
			if (ext.equalsIgnoreCase("zip")) return(new ZipInputStream(conn.getInputStream()));
		}
		
		return(conn.getInputStream());
	}

	private static String msDateString() {
		SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
		fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
		return(fmt.format(new Date()));
	}

	// +------------+
	// | Entrypoint |
	// +------------+

	// relies on DefaultAzureCredential

	public static void main(String[] args) throws Exception {

		if (args.length < 2) {
			usage();
			return;
		}

		// uses defaultazurecredential always
		AgateImport agate = AgateImport.createDefault(new Config());

		switch (args[0].toLowerCase()) {

			case "pipeline":
				List<PipelineSample> psamples = agate.listSamplesPipelineAsync(args[1]).get();
				for (PipelineSample s : psamples) {
					System.out.println(String.format("%s\t%s\t%s\n%s\n", s.Name, s.Project, s.Date, s.TsvPath));
				}
				break;

			case "tsv":
				int maxLines = (args.length > 2 ? Integer.parseInt(args[2]) : 10);
				InputStream stm = agate.getTsvStreamAsync(args[1]).get();
				printHead(stm, maxLines);
				stm.close();
				break;

			default:
				usage();
				break;
		}
		
		agate.close();
		Exec.shutdownPool();
	}

	private static void printHead(InputStream stm, int lines) throws Exception {

		InputStreamReader rdr = new InputStreamReader(stm);
		BufferedReader buf = new BufferedReader(rdr);

		boolean allLines = (lines == -1);
		int i = 0;
		String line;
		
		while ((allLines || i < lines) && (line = buf.readLine()) != null) {
			System.out.println(line);
			++i;
		}
		
		buf.close();
		rdr.close();
	}

	private static void usage() {
		System.out.println("Arguments: ");
		System.out.println("\tsamples SUBSTRING");
		System.out.println("\ttsv PATH");
	}

	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private AzureTokenFactory tokenFactory;

	private final static Logger log = Logger.getLogger(AgateImport.class.getName());
}
