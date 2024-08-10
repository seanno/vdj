//
// REPERTOIRESTORE_BLOBS.JAVA
// 

// Notes: currently expects all files to be in one container. Did it that way
// because name limits on containers are inconvenient for unique userid-derived
// values (particularly the 63 character limit). Azure says can have unlimited
// files in a container, and we're not doing direct-access or download here, so
// that all seems ok. Adding algorithmic partitioning would be easy; saving the
// container per userid slightly harder. Not worrying more about it now.

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;

import com.shutdownhook.vdj.vdjlib.model.Repertoire;

public class RepertoireStore_Blobs implements RepertoireStore
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		// Proivde one of these 
		public String ConnectionString; // key-based auth, prefix with "@" to use env var
		public String Endpoint; // DefaultAzureCredential

		public String ContainerName = "vdj"; // must adhere to container name rules, obv.
		public String ContextFileName = "context.json";
	}

	public RepertoireStore_Blobs(Config cfg) {
		
		this.cfg = cfg;
		this.client = getContainerClient();
	}

	// +-----------------+
	// | getUserContexts |
	// +-----------------+
	
	public String[] getUserContexts(String userId) {
		try {
			List<String> contexts = new ArrayList<String>();

			client.listBlobsByHierarchy(getUserPath(userId)).forEach(blob -> {
				 if (blob.isPrefix()) {
					 // trim off trailing "/" and leave only final path element
					 String name = blob.getName();
					 int cch = name.length();
					 if (cch < 2) return;
					 int ichLastSlash = name.lastIndexOf("/", cch - 2); // note ok if -1
					 contexts.add(Utility.urlDecode(name.substring(ichLastSlash + 1, cch - 1)));
				 }
			 });

			return(contexts.toArray(new String[contexts.size()]));
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "getUserContexts", false));
			return(null);
		}
	}

	// +-----------------------+
	// | getContextRepertoires |
	// +-----------------------+
	
	public Repertoire[] getContextRepertoires(String userId, String ctx) {
		try {
			String json = getContextFileBlob(userId, ctx).downloadContent().toString();
			return(Repertoire.fromJsonArray(json));
		}
		catch (BlobStorageException bse) {
			if (bse.getResponse().getStatusCode() == 404) {
				return(new Repertoire[0]);
			}
			else {
				log.warning(Utility.exMsg(bse, "getContextRepertoires", false));
				return(null);
			}
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "getContextRepertoires", false));
			return(null);
		}
	}

	// +---------------------+
	// | getRepertoireStream |
	// +---------------------+
	
	public InputStream getRepertoireStream(RepertoireSpec spec) {
		try {
			return(getRepertoireBlob(spec).openInputStream());
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "getRepertoireStream", false));
			return(null);
		}
	}

	// +-------------------------+
	// | getRepertoireSaveStream |
	// +-------------------------+

	public OutputStream getRepertoireSaveStream(RepertoireSpec spec) {
		try {
			return(getRepertoireBlob(spec)
				   .getBlockBlobClient()
				   .getBlobOutputStream(false));
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "getRepertoireSaveStream", false));
			return(null);
		}
	}
	
	// +---------------------------+
	// | commitRepertoireToContext |
	// +---------------------------+

	public boolean commitRepertoireToContext(String userId, String ctx, Repertoire r) {

		Repertoire[] newReps = Repertoire.append(getContextRepertoires(userId, ctx), r);
		return(saveContextRepertoires(userId, ctx, newReps));
	}

	// +------------------+
	// | deleteRepertoire |
	// +------------------+

	public boolean deleteRepertoire(RepertoireSpec spec) {

		try {
			// remove from context
			Repertoire[] newReps = Repertoire.remove(getContextRepertoires(spec.UserId, spec.Context), spec.Name);
			if (newReps.length == 0) {
				// by doing this we effectively delete the context too, since
				// there is no "directory" when there are no files in it.
				getContextFileBlob(spec.UserId, spec.Context).delete();
			}
			else if (!saveContextRepertoires(spec.UserId, spec.Context, newReps)) {
				return(false);
			}

			// remove file
			getRepertoireBlob(spec).delete();

			// clean up secondary files
			deleteRepertoireSecondaryFiles(spec);
				
			return(true);
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "deleteRepertoire", false));
			return(false);
		}
	}

	// +----------------------------------+
	// | getRepertoireSecondarySaveStream |
	// +----------------------------------+

	public OutputStream getRepertoireSecondarySaveStream(RepertoireSpec spec, String key) {
		try {
			return(getRepertoireSecondaryFileBlob(spec, key)
				   .getBlockBlobClient()
				   .getBlobOutputStream(true));
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "getRepertoireSecondarySaveStream", false));
			return(null);
		}
	}

	// +------------------------------+
	// | getRepertoireSecondaryStream |
	// +------------------------------+
	
	public InputStream getRepertoireSecondaryStream(RepertoireSpec spec, String key) {
		try {
			BlobClient blob = getRepertoireSecondaryFileBlob(spec, key);
			return(blob.exists() ? blob.openInputStream() : null);
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "getRepertoireSecondaryStream", true));
			return(null);
		}
	}

	// +--------------------------------+
	// | deleteRepertoireSecondaryFiles |
	// +--------------------------------+
	
	public boolean deleteRepertoireSecondaryFiles(RepertoireSpec spec) {
		try {
			client.listBlobsByHierarchy(getRepertoireCachePath(spec)).forEach(blob -> {
				if (!blob.isPrefix()) client.getBlobClient(blob.getName()).delete();
			});

			return(true);
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "deleteRepertoireSecondaryFiles", true));
			return(false);
		}
	}

	// +--------------------+
	// | getContainerClient |
	// +--------------------+

	private BlobContainerClient getContainerClient() {

		BlobServiceClient svc = (cfg.Endpoint == null
						 		 ? getStorageClientConnectionString(cfg.ConnectionString)
								 : getStorageClientEndpoint(cfg.Endpoint));


		log.info("Blob Container Name is: " + cfg.ContainerName);
		
		svc.createBlobContainerIfNotExists(cfg.ContainerName);
		return(svc.getBlobContainerClient(cfg.ContainerName));
	}

	private static BlobServiceClient getStorageClientEndpoint(String endpoint) {

		String resolved = getFromCfgOrEnv(endpoint);
		
		log.info("Connecting to Repertoire Blob Storage with DefaultAzureCredential");
		log.info(String.format("Blob Endpoint Info: %s (%s)", endpoint, resolved));

		return(new BlobServiceClientBuilder()
			   .endpoint(resolved)
			   .credential(new DefaultAzureCredentialBuilder().build())
			   .buildClient());
	}
	
	private static BlobServiceClient getStorageClientConnectionString(String connectionString) {

		log.info("Connecting to Azure storage with connection string");
		
		return(new BlobServiceClientBuilder()
			   .connectionString(getFromCfgOrEnv(connectionString))
			   .buildClient());
	}

	// +---------+
	// | Helpers |
	// +---------+

	private String getUserPath(String userId) {
		return(clean(userId) + "/");
	}

	private String getContextPath(String userId, String ctx) {
		return(getUserPath(userId) + clean(ctx) + "/");
	}

	private String getContextFilePath(String userId, String ctx) {
		return(getContextPath(userId, ctx) + cfg.ContextFileName);
	}

	private BlobClient getContextFileBlob(String userId, String ctx) {
		return(client.getBlobClient(getContextFilePath(userId, ctx)));
	}

	private String getRepertoirePath(RepertoireSpec spec) {
		return(getContextPath(spec.UserId, spec.Context) + clean(spec.Name) + TSV_EXT);
	}

	private BlobClient getRepertoireBlob(RepertoireSpec spec) {
		return(client.getBlobClient(getRepertoirePath(spec)));
	}

	private String getRepertoireCachePath(RepertoireSpec spec) {
		return(getContextPath(spec.UserId, spec.Context) + clean(spec.Name) + CACHE_SUFFIX + "/");
	}

	private BlobClient getRepertoireSecondaryFileBlob(RepertoireSpec spec, String key) {
		return(client.getBlobClient(getRepertoireCachePath(spec) + clean(key)));
	}
	
	private String clean(String input) {
		return(Utility.urlEncode(input));
	}

	private boolean saveContextRepertoires(String userId, String ctx, Repertoire[] reps) {

		try {
			String json = Repertoire.toJsonArray(reps);
			BlobClient blob = getContextFileBlob(userId, ctx);
			blob.upload(BinaryData.fromString(json), true);

			return(true);
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "saveRepertoireToContext", false));
			return(false);
		}
	}

	private static String getFromCfgOrEnv(String input) {
		return(input.startsWith("@") ? System.getenv(input.substring(1)) : input);
	}
	
	// +---------+
	// | Members |
	// +---------+

	private Config cfg;
	private BlobContainerClient client;

	private final static String TSV_EXT = ".tsv";
	private final static String CACHE_SUFFIX = "__cache";

	private final static Logger log = Logger.getLogger(RepertoireStore_Blobs.class.getName());
}
