//
// AZURETOKENFACTORY.JAVA
//

package com.shutdownhook.vdj.vdjlib;

import java.util.logging.Logger;

import com.google.gson.Gson;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.OnBehalfOfCredentialBuilder;
import com.azure.identity.UsernamePasswordCredentialBuilder;

public class AzureTokenFactory
{
	// +--------------+
	// | Factory Type |
	// +--------------+

	public enum FactoryType
	{
		Default,
		UserPass,
		OnBehalfOf,
		DeviceCode,
		CLI
	}
	
	// +--------+
	// | create |
	// +--------+
	
	public static AzureTokenFactory create() {
		return(create(FactoryType.Default, new DefaultParams(null)));
	}

	public static AzureTokenFactory create(FactoryType factoryType, Object params) {

		TokenCredential cred;
		
		switch (factoryType) {
			case Default: cred = getCredential_Default((DefaultParams) params); break;
			case CLI: cred = getCredential_CLI((DefaultParams) params); break;
			case UserPass: cred = getCredential_UserPass((UserPassParams) params); break;
			case OnBehalfOf: cred = getCredential_OBO((OnBehalfOfParams) params); break;
			default: return(null);
		}

		return(new AzureTokenFactory(cred));
	}
	
	// +----------------+
	// | Implementation |
	// +----------------+

	private AzureTokenFactory(TokenCredential cred) {
		this.cred = cred;
		this.staticToken = null;
	}

	private AzureTokenFactory(String token) {
		this.cred = null;
		this.staticToken = token;
	}

	public String getToken(String scope) {
		if (staticToken != null) return(staticToken);
		TokenRequestContext ctx = new TokenRequestContext().addScopes(scope);
		return(cred.getTokenSync(ctx).getToken());
	}

	public TokenCredential getCredential() {
		return(cred);
	}

	// +-------------+
	// | Device Code |
	// +-------------+

	// Creates a factory wrapping a pre-acquired token string, e.g. from
	// an OAuth2 device code flow managed externally by the caller.

	public static AzureTokenFactory createFromToken(String token) {
		return(new AzureTokenFactory(token));
	}

	// +-----------------------+
	// | getCredential_Default |
	// | getCredential_CLI     |
	// +-----------------------+

	public static class DefaultParams
	{
		public DefaultParams(String tenantId) {
			this.tenantId = tenantId;
		}

		private String tenantId;
	}
	
	// https://learn.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential
	// Tries a bunch of different types, including CLI and Machine Identities

	private static TokenCredential getCredential_Default(DefaultParams params) {
		DefaultAzureCredentialBuilder builder = new DefaultAzureCredentialBuilder();
		if (params.tenantId != null) builder.tenantId(params.tenantId);
		return(builder.build());
	}

	private static TokenCredential getCredential_CLI(DefaultParams params) {
		AzureCliCredentialBuilder builder = new AzureCliCredentialBuilder();
		if (params.tenantId != null) builder.tenantId(params.tenantId);
		return(builder.build());
	}

	// +---------------------+
	// | Username / Password |
	// +---------------------+

	// https://learn.microsoft.com/en-us/java/api/com.azure.identity.usernamepasswordcredential
	// Note this will not work if the user has MFA enabled!
	
	public static class UserPassParams
	{
		public UserPassParams(String user, String password, String tenantId, String clientId) {
			this.user = user;
			this.password = password;
			this.tenantId = tenantId;
			this.clientId = clientId;
		}
			
		private String user;
		private String password;
		private String tenantId;
		private String clientId;
	}

	private static TokenCredential getCredential_UserPass(UserPassParams params) {

		return(new UsernamePasswordCredentialBuilder()
			   .tenantId(params.tenantId)
			   .clientId(params.clientId)
			   .username(params.user)
			   .password(params.password)
			   .build());
	}

	// +------------+
	// | OnBehalfOf |
	// +------------+

	// https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-on-behalf-of-flow
	// Swaps a token (e.g., from Entra EasyAuth) for another "on behalf of" the first token user

	// * token MUST have an aud property equal to the client id, NOT MSGraph.
	// * Add CLIENTID/user_impersonation to the API permissions for the app
	// * Ensure user_impersonation for other resources (scopes) also in API permissions
	// * in loginParameters via Resource Explorer:
	//   - subscriptions/SUB/resourceGroups/GROUP/providers/Microsoft.App/containerApps/APP/authConfigs/current
	//   - "loginParameters": [ "scope=openid profile email CLIENTID/user_impersonation" ]
	// * Note setting values with RE seems to reset the Token Store settings sometimes, reapply as before if so

	public static class OnBehalfOfParams
	{
 		public OnBehalfOfParams(String clientSecret, String token) {

			this.clientSecret = clientSecret;
			this.token = token;
		}

		public OnBehalfOfParams(String clientSecret, String token, String clientId, String tenantId) {
			this(clientSecret, token);
			this.clientId = clientId;
			this.tenantId = tenantId;
		}
		
		private String clientSecret; // System.getenv("microsoft-provider-authentication-secret")
		private String token; // X-MS-TOKEN-AAD-ACCESS-TOKEN
		private String tenantId;
		private String clientId;
	}

	public static class AccessTokenEssentials
	{
		public String aud;
		public String tid;
	}

	private static TokenCredential getCredential_OBO(OnBehalfOfParams params) {

		AccessTokenEssentials ate = null;

		if (params.clientId == null || params.tenantId == null) {
			String payloadEnc = params.token.split("\\.")[1];
			String payloadTxt = Utility.base64urlDecode(payloadEnc);
			ate = new Gson().fromJson(payloadTxt, AccessTokenEssentials.class);
		}

		return(new OnBehalfOfCredentialBuilder()
			   .clientId(params.clientId == null ? ate.aud : params.clientId)
			   .clientSecret(params.clientSecret)
			   .tenantId(params.tenantId == null ? ate.tid : params.tenantId)
			   .userAssertion(params.token)
			   .build());
	}
	
	// +---------+
	// | Members |
	// +---------+

	private TokenCredential cred;
	private String staticToken;

	private final static Logger log = Logger.getLogger(AzureTokenFactory.class.getName());
}

