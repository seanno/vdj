//
// AZUREDEVICEAUTH.JAVA
//

package com.shutdownhook.vdj.vdjlib;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.gson.Gson;

public class AzureDeviceAuth
{
	// +----------------+
	// | Config & Setup |
	// +----------------+

	public static class Config
	{
		public String TenantId;
		public String ClientId;
		public String Scope;
		public Integer TimeoutMillis = (5 * 60 * 1000);
	}

	// +--------------------+
	// | DeviceCodeChallenge |
	// +--------------------+

	// Returned by start(). Pass UserCode + VerificationUri to the user so
	// they can authenticate; hold DeviceCode to pass into subsequent check() calls.

	public static class DeviceCodeChallenge
	{
		public String DeviceCode;
		public String UserCode;
		public String VerificationUri;
		public Integer ExpiresIn;
		public Integer Interval;
		public String Message;
	}

	// +------------------+
	// | DeviceCodeStatus |
	// +------------------+

	// Returned by check(). When Status is "Ready", AccessToken holds the bearer
	// token; pass it to AzureTokenFactory.createFromToken() for Agate requests.

	public static class DeviceCodeStatus
	{
		public String Status; // "Pending", "Ready", "Failed"
		public String AccessToken;
		public Long ExpiresIn;
		public String Error;
	}

	// +-------+
	// | start |
	// +-------+

	// Calls the MS device authorization endpoint and returns a DeviceCodeChallenge.
	// Returns null on network or configuration error (see logs for detail).

	public static DeviceCodeChallenge start(Config cfg) {

		String url = String.format(MS_DEVICE_CODE_URL_FMT, cfg.TenantId);
		String body = String.format("client_id=%s&scope=%s",
			Utility.urlEncode(cfg.ClientId),
			Utility.urlEncode(cfg.Scope));

		String response = msPostForm(cfg, url, body);
		if (response == null) return(null);

		MsDeviceCodeResp msResp = new Gson().fromJson(response, MsDeviceCodeResp.class);

		if (!Utility.nullOrEmpty(msResp.error)) {
			log.warning(String.format("AzureDeviceAuth.start error: %s", response));
			return(null);
		}

		DeviceCodeChallenge result = new DeviceCodeChallenge();
		result.DeviceCode = msResp.device_code;
		result.UserCode = msResp.user_code;
		result.VerificationUri = msResp.verification_uri;
		result.ExpiresIn = msResp.expires_in;
		result.Interval = msResp.interval;
		result.Message = msResp.message;

		return(result);
	}

	// +-------+
	// | check |
	// +-------+

	// Polls the MS token endpoint with a device_code from a prior start() call.
	// Safe to call repeatedly; returns "Pending" until the user authenticates,
	// then "Ready" (with AccessToken) or "Failed" (with Error).

	public static DeviceCodeStatus check(Config cfg, String deviceCode) {

		String url = String.format(MS_TOKEN_URL_FMT, cfg.TenantId);
		String body = String.format("grant_type=%s&client_id=%s&device_code=%s",
			Utility.urlEncode("urn:ietf:params:oauth:grant-type:device_code"),
			Utility.urlEncode(cfg.ClientId),
			Utility.urlEncode(deviceCode));

		DeviceCodeStatus result = new DeviceCodeStatus();

		String response = msPostForm(cfg, url, body);
		if (response == null) {
			result.Status = "Failed";
			result.Error = "no response from authorization server";
			return(result);
		}

		MsTokenResp msResp = new Gson().fromJson(response, MsTokenResp.class);

		if (!Utility.nullOrEmpty(msResp.error)) {
			if (msResp.error.equals("authorization_pending") ||
				msResp.error.equals("slow_down")) {
				result.Status = "Pending";
			}
			else {
				// expired_token, authorization_declined, bad_verification_code, etc.
				result.Status = "Failed";
				result.Error = Utility.nullOrEmpty(msResp.error_description)
					? msResp.error : msResp.error_description;
			}
		}
		else {
			result.Status = "Ready";
			result.AccessToken = msResp.access_token;
			result.ExpiresIn = msResp.expires_in;
		}

		return(result);
	}

	// +---------+
	// | Helpers |
	// +---------+

	private static String MS_DEVICE_CODE_URL_FMT =
		"https://login.microsoftonline.com/%s/oauth2/v2.0/devicecode";

	private static String MS_TOKEN_URL_FMT =
		"https://login.microsoftonline.com/%s/oauth2/v2.0/token";

	private static String msPostForm(Config cfg, String urlStr, String formBody) {

		InputStream stm = null;

		try {
			URL url = new URL(urlStr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setConnectTimeout(cfg.TimeoutMillis);
			conn.setReadTimeout(cfg.TimeoutMillis);

			byte[] rgb = formBody.getBytes(StandardCharsets.UTF_8);
			OutputStream out = conn.getOutputStream();
			out.write(rgb);
			out.flush();
			out.close();

			int status = conn.getResponseCode();

			// MS returns error details in the body even for 4xx responses
			stm = (status < 400 ? conn.getInputStream() : conn.getErrorStream());
			return(Utility.stringFromInputStream(stm));
		}
		catch (Exception e) {
			log.warning(Utility.exMsg(e, "msPostForm " + urlStr, false));
			return(null);
		}
		finally {
			Utility.safeClose(stm);
		}
	}

	// used only for parsing MS responses; fields intentionally snake_case to match JSON
	private static class MsDeviceCodeResp
	{
		String device_code, user_code, verification_uri, message, error;
		int expires_in, interval;
	}

	private static class MsTokenResp
	{
		String access_token, error, error_description;
		long expires_in;
	}

	private final static Logger log = Logger.getLogger(AzureDeviceAuth.class.getName());
}
