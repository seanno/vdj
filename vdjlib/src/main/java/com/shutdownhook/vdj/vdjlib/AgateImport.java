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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

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

		CompletableFuture<InputStream> future = new CompletableFuture<InputStream>();

		Exec.getPool().submit(() -> {

			try {
				future.complete(getTsvStream(tsvPath));
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "getTsvStreamAsync", true));
				future.complete(null);
			}
		});

		return(future);
	}
	
	public InputStream getTsvStream(String tsvPath) throws IOException {

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

		return(conn.getInputStream());
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
		"  sample_name desc";
	
	public static class Sample
	{
		public String Id;
		public String Name;
		public Date Date;
		public long TotalCells;
		public double TotalMilliliters = 0.0; // agate doesn't support this yet
		public String TsvPath;
	}
	
	public CompletableFuture<List<Sample>> listSamplesAsync(String search) {

		CompletableFuture<List<Sample>> future = new CompletableFuture<List<Sample>>();

		Exec.getPool().submit(() -> {

			try {
				future.complete(listSamples(search));
			}
			catch (Exception e) {
				log.warning(Utility.exMsg(e, "listSamplesAsync", true));
				future.complete(null);
			}
		});

		return(future);
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
				s.Date = rs.getDate("effective_date");
				s.TsvPath = rs.getString("current_rearrangement_tsv_file");

				Double dblCells = rs.getDouble("sample_cells");
				if (dblCells == null) dblCells = rs.getDouble("sample_cells_mass_estimate");
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
