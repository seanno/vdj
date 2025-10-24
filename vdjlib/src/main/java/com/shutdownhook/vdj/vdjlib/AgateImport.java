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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipException;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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

	private static String DOWNLOAD_URL_FMT =
		"%s/clearinghouse/sequencing_run/%s/files/download";


	public CompletableFuture<InputStream> getTsvStreamAsync(String itemId) {
		return(Exec.runAsync("getTsvStream", new Exec.AsyncOperation() {
			public InputStream execute() throws Exception {
				return(getTsvStream(itemId));
			}
		}));
	}
	
	public InputStream getTsvStream(String itemId) throws IOException {
		String url = getDownloadUrl(itemId);
		return(getInputStream(url, null));
	}

	private String getDownloadUrl(String itemId) throws IOException {
		
		InputStream stm = null;

		try {
			String url = String.format(DOWNLOAD_URL_FMT, cfg.ApiBaseUrl, itemId);

			stm = getInputStream(url, cfg.ApiResource);
			return(Utility.stringFromInputStream(stm));
		}
		finally {
			Utility.safeClose(stm);
		}
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

	public static class PipelineSample implements Comparable<PipelineSample>
	{
		public String Name;
		public String Project;
		public LocalDate EffectiveDate;
		public LocalDate UploadDate;
		public String ItemId;
		
		public int compareTo(PipelineSample other) {
			int cmp = nullSafeCompare(EffectiveDate, other.EffectiveDate) * -1;
			if (cmp == 0) cmp = nullSafeCompare(Name, other.Name) * -1;
			if (cmp == 0) cmp = nullSafeCompare(UploadDate, other.UploadDate) * -1; // take newest
			return(cmp);
		}

		public boolean equals(Object other) {
			if (other == null) return(false);
			if (!(other instanceof PipelineSample)) return(false);
			return(compareTo((PipelineSample)other) == 0);
		}
		
		private static <T extends Comparable> int nullSafeCompare(T t1, T t2) {
			if (t1 == null && t2 == null) return(0);
			if (t1 == null && t2 != null) return(-1);
			if (t1 != null && t2 == null) return(1);
			return(t1.compareTo(t2));
		}
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
			JsonArray jsonSamples = new Gson().fromJson(json, JsonArray.class);

            List<PipelineSample> samples = new ArrayList<PipelineSample>();

			for (int i = 0; i < jsonSamples.size(); ++i) {
				
				JsonObject jsonSample = jsonSamples.get(i).getAsJsonObject();
					
				PipelineSample sample = new PipelineSample();
				samples.add(sample);
				
				sample.Name = traverseToString(jsonSample, "Identity", "Name");
				sample.ItemId = traverseToString(jsonSample, "ItemId");

				sample.Project = traverseToString(jsonSample, "Project", "Name");
				if (sample.Project == null) sample.Project = traverseToString(jsonSample, "RawMetadata", "OrderName");

				String dateStr = traverseToString(jsonSample, "UploadDate");
				sample.UploadDate = safeParseLocalDate(dateStr);

				dateStr = traverseToString(jsonSample, "RawMetadata", "CollectionDate");
				sample.EffectiveDate = safeParseLocalDate(dateStr);
				if (sample.EffectiveDate == null) sample.EffectiveDate = sample.UploadDate;
			}

			List<PipelineSample> expandedSamples = maybeExpandPatientSearch(search, samples);
			if (expandedSamples != null) {
				log.info("Expanded patient search for " + search);
				return(expandedSamples);
			}

			Collections.sort(samples);
			samples = filterMultipleUploads(samples);

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

	private List<PipelineSample> filterMultipleUploads(List<PipelineSample> inputSamples) {

		HashSet<String> seen = new HashSet<String>();
		
		List<PipelineSample> filteredSamples = new ArrayList<PipelineSample>();
		for (int i = 0; i < inputSamples.size(); ++i) {
			PipelineSample thisSample = inputSamples.get(i);
			if (seen.contains(thisSample.Name)) continue;
			filteredSamples.add(thisSample);
			seen.add(thisSample.Name);
		}

		return(filteredSamples);
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

		if (resource != null) {
			String token = tokenFactory.getToken(resource); 
			conn.setRequestProperty("Authorization", "Bearer " + token);
			conn.setRequestProperty("x-ms-date", msDateString());
			conn.setRequestProperty("x-ms-version", cfg.StorageVersion);
		}
				
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

			int ichQuestion = path.indexOf("?");
			String justPath = (ichQuestion == -1 ? path : path.substring(0, ichQuestion));
				
			int ichLastDot = justPath.lastIndexOf(".");
			String ext = (ichLastDot == -1 ? "" : justPath.substring(ichLastDot + 1));

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

	private static String traverseToString(JsonElement elt, String... children) {

		// super-duper-defensive
		
		JsonElement walk = elt;
		for (int i = 0; i < children.length - 1; ++i) {
			walk  = walk.getAsJsonObject().get(children[i]);
			if (walk == null) return(null);
		}

		walk = walk.getAsJsonObject().get(children[children.length-1]);
		if (walk == null) return(null);

		String ret = walk.getAsString();
		return(Utility.nullOrEmpty(ret) ? null : ret);
	}

	private LocalDate safeParseLocalDate(String input) {
		if (input == null) return(null);
		int ich = input.indexOf("T");
		return(LocalDate.parse(ich == -1 ? input : input.substring(0, ich)));
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
					System.out.println(String.format("%s\t%s\t%s\t%s\t%s", s.Name, s.Project,
													 s.EffectiveDate, s.UploadDate, s.ItemId));
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
