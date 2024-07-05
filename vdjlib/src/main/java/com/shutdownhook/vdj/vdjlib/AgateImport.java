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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

public class AgateImport implements Closeable
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String User; // null == DefaultAzureCredential
		public String Password;

		public String Server = "adaptiveagate-db.database.windows.net";
		public String Database = "agate";

		public Integer MinSearchLength = 3;
		
		public String AgateClientId = "fdcf242b-a25b-4b35-aff2-d91d8100225d";
		public String StorageVersion = "2017-11-09";
		public Integer StorageTimeoutMillis = (5 * 60 * 1000);
	}

	public AgateImport(Config cfg) throws SQLException {
		
		this.cfg = (cfg == null ? new Config() : cfg);
		setupConnection();
	}

	public void close() {
		if (cxn != null) safeClose(cxn);
	}
	
	// +--------------+
	// | getTsvStream |
	// +--------------+

	public InputStream getTsvStream(String tsvPath) throws IOException {

		URL url = new URL(tsvPath);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		conn.setFollowRedirects(true);
		conn.setConnectTimeout(cfg.StorageTimeoutMillis);
		conn.setReadTimeout(cfg.StorageTimeoutMillis);

		String token = getToken("https://storage.azure.com", false);
		conn.setRequestProperty("Authorization", "Bearer " + token);
		conn.setRequestProperty("x-ms-version", cfg.StorageVersion);
				
		int status = conn.getResponseCode();
		if (status < 200 || status >= 300) return(null);

		return(conn.getInputStream());
	}

	// +-------------+
	// | listSamples |
	// +-------------+

	private static String SAMPLES_SQL =
		"select " +
		"  item_id, " +
		"  sample_name, " +
		"  sample_cells, " +
		"  sample_cells_mass_estimate, " +
		"  current_rearrangement_tsv_file " +
		"from " +
		"  samples " +
		"where " +
		"  sample_name like concat('%',?,'%') " +
		"order by " +
		"  sample_name";
	
	public static class Sample
	{
		public String Id;
		public String Name;
		public long TotalCells;
		public String TsvPath;
	}
	
	public List<Sample> listSamples(String search) throws SQLException, IllegalArgumentException {

		if (search == null || search.length() < cfg.MinSearchLength) {
			throw new IllegalArgumentException(String.format("search must be at least %d chars",
															 cfg.MinSearchLength));
		}
		
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			stmt = cxn.prepareStatement(SAMPLES_SQL);
			stmt.setString(1, search);
			rs = stmt.executeQuery();

			List<Sample> samples = new ArrayList<Sample>();
			
			while (rs != null && rs.next()) {

				Sample s = new Sample();
				samples.add(s);
				
				s.Id = rs.getString("item_id");
				s.Name = rs.getString("sample_name");
				s.TsvPath = rs.getString("current_rearrangement_tsv_file");

				Double dblCells = rs.getDouble("sample_cells");
				if (dblCells == null) dblCells = rs.getDouble("sample_cells_mass_estimate");
				if (dblCells != null) s.TotalCells = (long) Math.round(dblCells);
			}

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

	private void setupConnection() throws SQLException {

		SQLServerDataSource ds = new SQLServerDataSource();
		ds.setServerName(cfg.Server);
		ds.setDatabaseName(cfg.Database);

		if (cfg.User != null) {
			ds.setUser(cfg.User);
			ds.setPassword(cfg.Password);
		}
		else {
			ds.setAccessToken(getToken("https://database.windows.net/", true));
		}

		this.cxn = ds.getConnection();
	}

	private String getToken(String scope, boolean skipUserPass) {

		TokenCredential cred = null;
		
		if (cfg.User != null) {
			if (skipUserPass) return(null);
			cred = new UsernamePasswordCredentialBuilder()
				.clientId(cfg.AgateClientId)
				.username(cfg.User)
				.password(cfg.Password)
				.build();
		}
		else {
			cred = new DefaultAzureCredentialBuilder().build();
		}

		TokenRequestContext ctx = new TokenRequestContext().addScopes(scope);
		return(cred.getTokenSync(ctx).getToken());
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
		
		AgateImport agate = new AgateImport(new Config());

		switch (args[0].toLowerCase()) {

			case "samples":
				List<Sample> samples = agate.listSamples(args[1]);
				for (Sample s : samples) {
					System.out.println(String.format("%s\t%s\t%d\n%s\n", s.Id, s.Name, s.TotalCells, s.TsvPath));
				}
				break;

			case "tsv":
				int maxLines = (args.length > 2 ? Integer.parseInt(args[2]) : 10);
				InputStream stm = agate.getTsvStream(args[1]);
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
	private Connection cxn;

	private final static Logger log = Logger.getLogger(AgateImport.class.getName());
}
