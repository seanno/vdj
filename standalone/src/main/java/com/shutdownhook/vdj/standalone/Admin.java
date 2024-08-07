//
// ADMIN.JAVA
//

package com.shutdownhook.vdj.standalone;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

import com.azure.core.credential.TokenCredential;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.toolbox.WebServer;
import com.shutdownhook.vdj.vdjlib.AzureTokenFactory;
import com.shutdownhook.vdj.vdjlib.AzureTokenFactory.OnBehalfOfParams;

public class Admin
{
	// +-------------------+
	// | getRequestDetails |
	// +-------------------+

	public static class RequestDetails
	{
		public WebServer.Request Request;
		public JsonObject IdToken;
	}

	public static String getRequestDetails(WebServer.Request request) {
		
		RequestDetails deets = new RequestDetails();
		deets.Request = request;
		deets.IdToken = parseIdtoken(request);
		
		return(gson.toJson(deets));
	}
	
	private static JsonObject parseIdtoken(WebServer.Request request) {
		
		String idToken = request.getHeader("X-MS-TOKEN-AAD-ID-TOKEN");
		if (idToken == null) return(null);
		
		String payloadEnc = idToken.split("\\.")[1];
		String payloadTxt = Easy.base64urlDecode(payloadEnc);

		return(jsonParser.parse(payloadTxt).getAsJsonObject());
	}

	// +----------------------------+
	// | verifyBlobOnBehalfOfAccess |
	// +----------------------------+

	// This is just a test function to verify that OBO authentication is working
	// properly when XMS auth is being used. Provide any known blob url that
	// the user should have access to.
	
	public static boolean verifyBlobOBOAccess(String blobUrl,
											  WebServer.Request request,
											  String clientSecretEnv) throws Exception {

		String secret = System.getenv(clientSecretEnv);
		
		log.info(String.format("verifying OBO: blob=%s, secret=%s... (%d), token=%s",
							   blobUrl, secret.substring(0, 3), secret.length(),
							   request.User.Token));
		// token

		String token =
			AzureTokenFactory.create(AzureTokenFactory.FactoryType.OnBehalfOf,
									 new OnBehalfOfParams(secret,  request.User.Token))
			.getToken("https://storage.azure.com/.default");

		// verify connection opens ok
		
		HttpURLConnection conn = (HttpURLConnection) new URL(blobUrl).openConnection();

		conn.setFollowRedirects(true);
		conn.setConnectTimeout(5 * 60 * 1000);
		conn.setReadTimeout(5 * 60 * 1000);

		conn.setRequestProperty("Authorization", "Bearer " + token);
		conn.setRequestProperty("x-ms-version", "2017-11-09");
				
		int status = conn.getResponseCode();
		return(status >= 200 && status < 300);
	}

	private static String getOnBehalfOfToken(WebServer.Request request,
											 String clientSecretEnv) {
		
		OnBehalfOfParams oboParams = new OnBehalfOfParams(System.getenv(clientSecretEnv),
														  request.User.Token);

		// log.info("OBOPARAMS: " + gson.toJson(oboParams));

		AzureTokenFactory factory =
			AzureTokenFactory.create(AzureTokenFactory.FactoryType.OnBehalfOf, oboParams);
		
		return(factory.getToken("https://storage.azure.com/.default"));
	}
	
	// +---------+
	// | Helpers |
	// +---------+

	private final static Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private final static JsonParser jsonParser = new JsonParser();
	
	private final static Logger log = Logger.getLogger(Admin.class.getName());
}

