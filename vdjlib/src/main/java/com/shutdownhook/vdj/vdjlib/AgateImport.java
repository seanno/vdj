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
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
		public String Server = "adaptiveagate-db.database.windows.net";
		public String Database = "agate";

		public Integer MinSearchLength = 5;
		public Boolean EnablePatientSearchExpansion = true;
		
		public String AgateClientId = "fdcf242b-a25b-4b35-aff2-d91d8100225d";
		public String AgateTenantId = "720cf133-4325-491c-b6a9-159d0497fc65";

		public String StorageResource = "https://storage.azure.com/.default";
		public String StorageVersion = "2017-11-09";
		public Integer StorageTimeoutMillis = (5 * 60 * 1000);

		public String SqlResource = "https://database.windows.net/.default";
	}

	public AgateImport(Config cfg, AzureTokenFactory tokenFactory) {
		this.cfg = cfg;
		this.tokenFactory = tokenFactory;
	}

	public void close() {
		if (cxn != null) safeClose(cxn);
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

		try {
			return(getTsvStreamInternal(tsvPath, true));
		}
		catch (ZipException ze) {
			log.warning(String.format("Agate says gz/zip but nope; falling back (%s)", tsvPath));
			return(getTsvStreamInternal(tsvPath, false));
		}
	}

	public InputStream getTsvStreamInternal(String tsvPath, boolean tryZips) throws IOException {

		URL url = new URL(tsvPath);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		conn.setFollowRedirects(true);
		conn.setConnectTimeout(cfg.StorageTimeoutMillis);
		conn.setReadTimeout(cfg.StorageTimeoutMillis);

		String token = tokenFactory.getToken(cfg.StorageResource); 
		conn.setRequestProperty("Authorization", "Bearer " + token);
		conn.setRequestProperty("x-ms-version", cfg.StorageVersion);
				
		int status = conn.getResponseCode();
		if (status < 200 || status >= 300) return(null);

		if (tryZips) {
			
			int ichLastDot = tsvPath.lastIndexOf(".");
			String ext = (ichLastDot == -1 ? "" : tsvPath.substring(ichLastDot + 1));

			if (ext.equalsIgnoreCase("gz")) return(new GZIPInputStream(conn.getInputStream()));
			if (ext.equalsIgnoreCase("zip")) return(new ZipInputStream(conn.getInputStream()));
		}
		
		return(conn.getInputStream());
	}

	// +--------------------------+
	// | listSamplesPipelineAsync |
	// | listSamplesPipeline      |
	// +--------------------------+

	private static String PIPELINE_SAMPLES_SQL =
		"select " +
		"  s.sample_name, " +
		"  coalesce(s.project_group_name, s.order_name) as group_name, " +
		"  coalesce(s.collection_date, s.upload_date) as effective_date, " +
		"  rf.path as pipeline_tsv_path " +
		"from  " +
		"  samples s " +
		"inner join " +
		"  clearinghouse_sequencing_run csr " +
		"  on s.sample_name = csr.identity_name " +
		"inner join " +
		"  related_files rf " +
		"  on csr.item_id = rf.item_id and rf.file_key = 'PIPELINE_TSV' " +
		"where " +
		"  s.sample_name like concat('%',?,'%') or " +
		"  project_group_name like concat('%',?,'%') or " +
		"  order_name like concat('%',?,'%') " +
		"order by " +
		"  effective_date desc, " +
		"  s.sample_name desc, " +
		"  rf.file_version desc ";
	
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
	
	public List<PipelineSample> listSamplesPipeline(String search) throws SQLException, IllegalArgumentException {

		ensureConnection();
		
		if (search == null || search.length() < cfg.MinSearchLength) {
			throw new IllegalArgumentException(String.format("search must be at least %d chars",
															 cfg.MinSearchLength));
		}
		
		log.info(String.format("Fetching Agate (pipeline) samples matching %s", search));

		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			stmt = cxn.prepareStatement(PIPELINE_SAMPLES_SQL);
			stmt.setString(1, search);
			stmt.setString(2, search);
			stmt.setString(3, search);
			rs = stmt.executeQuery();

			log.info("Starting Agate pipeline fetch loop");

			List<PipelineSample> samples = new ArrayList<PipelineSample>();
			String lastName = null;
			
			while (rs != null && rs.next()) {

				String thisName = rs.getString("sample_name");
				if (lastName != null && lastName.equals(thisName)) continue;
				lastName = thisName;
				
				PipelineSample s = new PipelineSample();
				samples.add(s);
				
				s.Name = thisName;
				s.Project = rs.getString("group_name");
				s.TsvPath = rs.getString("pipeline_tsv_path");

				Date d = rs.getDate("effective_date");
				s.Date = (d == null ? null : d.toLocalDate());
			}

			List<PipelineSample> expandedSamples = maybeExpandPatientSearch(search, samples);
			if (expandedSamples != null) {
				log.info("Expanded patient search for " + search);
				return(expandedSamples);
			}

			log.info(String.format("Fetched %d (pipeline) samples matching %s", samples.size(), search));
			return(samples);
		}
		finally {
			if (rs != null) safeClose(rs);
			if (stmt != null) safeClose(stmt);
		}
	}

	// this helper turns "D-*" searches that return a samples that matches 
	// the clinical sample format into a patient id search ... so that external users
	// can use a D- number to find all samples for a patient
	private List<PipelineSample> maybeExpandPatientSearch(String search,
														  List<PipelineSample> samples)
		throws SQLException, IllegalArgumentException {

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

	// +------------------+
	// | listSamplesAsync |
	// | listSamples      |
	// +------------------+

	private static String SAMPLES_SQL =
		"select " +
		"  item_id, " +
		"  sample_name, " +
		"  sample_cells, " +
		"  sample_cells_mass_estimate, " +
		"  coalesce(collection_date, upload_date) as effective_date, " +
		"  current_rearrangement_tsv_file " +
		"from " +
		"  samples " +
		"where " +
		"  sample_name like concat('%',?,'%') " +
		"order by " +
		"  effective_date desc, " + 
		"  sample_name desc";
	
	public static class Sample
	{
		public String Id;
		public String Name;
		public LocalDate Date;
		public long TotalCells;
		public double TotalMilliliters = 0.0; // agate doesn't support this yet
		public String TsvPath;
	}
	
	public CompletableFuture<List<Sample>> listSamplesAsync(String search) {
		return(Exec.runAsync("listSamples", new Exec.AsyncOperation() {
			public List<Sample> execute() throws Exception {
				return(listSamples(search));
			}
		}));
	}
	
	public List<Sample> listSamples(String search) throws SQLException, IllegalArgumentException {

		ensureConnection();
		
		if (search == null || search.length() < cfg.MinSearchLength) {
			throw new IllegalArgumentException(String.format("search must be at least %d chars",
															 cfg.MinSearchLength));
		}
		
		log.info(String.format("Fetching Agate samples matching %s", search));

		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			stmt = cxn.prepareStatement(SAMPLES_SQL);
			stmt.setString(1, search);
			rs = stmt.executeQuery();
			log.info("Starting Agate fetch loop");

			List<Sample> samples = new ArrayList<Sample>();
			
			while (rs != null && rs.next()) {

				Sample s = new Sample();
				samples.add(s);
				
				s.Id = rs.getString("item_id");
				s.Name = rs.getString("sample_name");
				s.TsvPath = rs.getString("current_rearrangement_tsv_file");

				Date d = rs.getDate("effective_date");
				s.Date = (d == null ? null : d.toLocalDate());
				
				Double dblCells = rs.getDouble("sample_cells");
				if (dblCells == null || dblCells == 0.0) dblCells = rs.getDouble("sample_cells_mass_estimate");
				if (dblCells != null) s.TotalCells = (long) Math.round(dblCells);
			}

			log.info(String.format("Fetched %d Agate samples matching %s", samples.size(), search));
			return(samples);
		}
		finally {
			if (rs != null) safeClose(rs);
			if (stmt != null) safeClose(stmt);
		}
	}

	// +-------+
	// | query |
	// +-------+

	public static class AgateQueryResults
	{
		public String[] Headers;
		public List<String[]> Rows = new ArrayList<String[]>();
		public String Error;
	}
	
	public AgateQueryResults query(String query) {
		
		Statement stmt = null;
		ResultSet rs = null;

		AgateQueryResults results = new AgateQueryResults();

		try {
			ensureConnection();
			
			stmt = cxn.createStatement();
			rs = stmt.executeQuery(query);

			ResultSetMetaData metaData = rs.getMetaData();
			int ccol = metaData.getColumnCount();
			
			results.Headers = new String[ccol];
			for (int i = 0; i < ccol; ++i) results.Headers[i] = metaData.getColumnLabel(i+1);
			
			while (rs != null && rs.next()) {
				String[] cols = new String[ccol];
				results.Rows.add(cols);
				for (int i = 0; i < ccol; ++i) cols[i] = rs.getString(i+1);
			}
		}
		catch (Exception e) {
			results.Error = e.toString();
		}
		finally {
			if (rs != null) safeClose(rs);
			if (stmt != null) safeClose(stmt);
		}
		
		return(results);
	}
		
	// +---------+
	// | Helpers |
	// +---------+

	private void ensureConnection() throws SQLException {

		if (cxn != null) return;
		
		log.info(String.format("Setting up agate db connection to %s/%s",
							   cfg.Server, cfg.Database));
				 
		SQLServerDataSource ds = new SQLServerDataSource();
		ds.setServerName(cfg.Server);
		ds.setDatabaseName(cfg.Database);
		ds.setAccessToken(tokenFactory.getToken(cfg.SqlResource));

		// https://stackoverflow.com/questions/961078/sql-server-query-running-slow-from-java
		ds.setSendStringParametersAsUnicode(false);

		this.cxn = ds.getConnection();

		log.info("Agate db connection established");
	}

	private void safeClose(AutoCloseable c) {
		try { c.close(); }
		catch (Exception e) { /* eat it */ }
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

			case "samples":
				List<Sample> samples = agate.listSamplesAsync(args[1]).get();
				for (Sample s : samples) {
					System.out.println(String.format("%s\t%s\t%d\n%s\n", s.Id, s.Name, s.TotalCells, s.TsvPath));
				}
				break;

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
	private Connection cxn;

	private final static Logger log = Logger.getLogger(AgateImport.class.getName());
}
