//
// AZURETOKENFACTORY.JAVA
//

package com.shutdownhook.vdj.vdjlib;

import java.util.Logger;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.OnBehalfOfCredentialBuilder;
import com.azure.identity.UsernamePasswordCredentialBuilder;

public class AzureTokenFactory
{
	// +----------+
	// | getToken |
	// +----------+

	public static String getToken(String scope, TokenCredential cred) {
		TokenRequestContext ctx = new TokenRequestContext().addScopes(scope);
		return(cred.getTokenSync(ctx).getToken());
	}

	// +---------+
	// | Default |
	// +---------+

	// https://learn.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential
	// Tries a bunch of different types, including CLI and Machine Identities
	
	public static TokenCredential getTokenCredential() {
		return(new DefaultAzureCredentialBuilder().build());
	}

	// +---------------------+
	// | Username / Password |
	// +---------------------+

	// https://learn.microsoft.com/en-us/java/api/com.azure.identity.usernamepasswordcredential
	// Note this will not work if the user has MFA enabled!
	
	public static class UserPassParams
	{
		public String User;
		public String Password;
		public String TenantId;
		public String ClientId;
	}

	public static TokenCredential getTokenCredential(UserPassParams params) {

		return(new UsernamePasswordCredentialBuilder()
			   .tenantId(params.TenantId)
			   .clientId(params.ClientId)
			   .username(params.User)
			   .password(params.Password)
			   .build());
	}

	// +------------+
	// | OnBehalfOf |
	// +------------+

	// https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-on-behalf-of-flow
	// https://learn.microsoft.com/en-us/java/api/com.azure.identity.onbehalfofcredentialbuilder
	// Swaps a token (e.g., from Entra EasyAuth) for another "on behalf of" the first token user

	public static class OnBehalfOfParams
	{
		
	}

	public static TokenCredential getTokenCredential(OnBehalfOfParams params) {
	}

	// +---------+
	// | Members |
	// +---------+
	
	private final static Logger log = Logger.getLogger(AzureTokenFactory.class.getName());
}

