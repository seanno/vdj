# vdj
An open source library and web application for working with T- and B-cell repertoires as created by [Adaptive Biotechnologies](https://adaptivebiotech.com). Native Adaptive pipeline files and [immunoSEQ Analyzer](https://clients.adaptivebiotech.com) v2 and v3 export files can be used. 
* [vdjlib](vdjlib) contains code for working with repertoires. It depends on the gson library only.
* [client](client) is a React application that provides a UX for working with repertoires. 
* [standalone](standalone) is a self-contained web server that serves client and server components for a complete solution. It depends on the [shutdownhook toolbox](https://github.com/seanno/shutdownhook/tree/main/toolbox) library.

# building
Development system requirement is a Linux box with git, JDK (v11+), Maven, NodeJS and npm. JUnit tests require docker to be available and running as well (Azurite is launched in a container to support Azure-related tests).
```
git clone https://github.com/seanno/shutdownhook.git
git clone https://github.com/seanno/vdj
cd vdj/standalone
./fullbuild.sh
```
# running (standalone)
The fullbuild.sh script embeds the webpacked client into the standalone uber JAR, which serves those static files together with the necessary server methods for a complete web application. From within the [standalone](standalone) directory, run the following command:
`java -cp target/vdj-standalone-1.0-SNAPSHOT.jar com.shutdownhook.vdj.standalone.app config-dev.json`
Point your browser at https://localhost:3001 to get started (note you'll have to accept the self-signed certificate).

# running (client development)
For client development with hot-reload capability, first run the standalone server as above. In a separate window, cd to the [client](client) directory and run:
`npm run dev -- --host 0.0.0.0`
Point your browser at https://localhost:5173 to access the client (again with a self-signed certificate). 

# running (container / Azure Web Site)
The [standalone/docker](standalone/docker) directory builds a container ready to run in an Azure App Service
Web App using built-in Active Directory (ok, Entra) authentication. See the dockerbuild.sh script for an example
of building and pushing the image. Basic steps are pretty simple:

1. Create a storage account
2. Create App Service / Web App
3. Specify your container
4. Add an environment variable "RepertoireStorageEndpoint" with the storage account endpoint, e.g., https://STORAGEACCTNAME.blob.core.windows.net/
5. Enable a system-assigned managed identity and grant it "Storage Blob Data Contributor" rights on the storage account.
6. Under Authentication, enable the Microsoft identity provider, creating a new app registration and ensuring that the token store is ON.

At this point anyone with an Azure AD account will be able to log in, upload repertoire files and use the system.

See the source code for optional use of custom app roles VDJ_UploadToAnyUserId, VDJ_AssumeUserId_XXX and VDJ_AdminUser.

If you would like to enable sample import from Adaptive's Agate system, do the following:

1. Add an environment variable "AgateAuthType" with the value "UserPass"
2. Under the client app API Permissions, add:
  * https://storage.azure.com/ user_impersonation
  * https://database.windows.net/ user_impersonation
  * Your client app user_impersonation
3. Using Azure resource explorer, navigate to subscriptions/SUB/resourceGroups/GROUP/providers/Microsoft.Web/sites/SITE/config/authSettingsV2 and under identityProviders/azureActiveDirectory/login add a field "loginParameters" with the value ["scope=openid profile email CLIENTID/user_impersonation"]
4. Troubleshooting:
  * If some users have already logged into the app, you may need to force admin re-consent by visiting the url https://login.microsoftonline.com/TENANTID/adminconsent?client_id=CLIENTID
  * If you still have trouble, try logging in with an incognito window and/or hitting https://YOURSITE/.auth/logout to force a relogin. There are a bunch of weird semi-documented caches in play here.
 
 

