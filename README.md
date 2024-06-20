# vdj
An open source library and web application for working with T- and B-cell repertoires as created by [Adaptive Biotechnologies](https://adaptivebiotech.com). Native Adaptive pipeline files and [immunoSEQ Analyzer](https://clients.adaptivebiotech.com) v2 and v3 export files can be used. 
* [vdjlib](vdjlib) contains code for working with repertoires. It depends on the gson library only.
* [client](client) is a React application that provides a UX for working with repertoires. 
* [standalone](standalone) is a self-contained web server that serves client and server components for a complete solution. It depends on the [shutdownhook toolbox](https://github.com/seanno/shutdownhook/tree/main/toolbox) library.

# building
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

  
