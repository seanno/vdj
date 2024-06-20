# vdj
An open source library and web application for working with T- and B-cell repertoires as created by Adaptive Biotechnologies. Native Adaptive pipeline files and immunoSEQ Analyzer v2 and v3 export files can be used. 
* [vdjlib](vdjlib) contains code for working with repertoires. It depends on the gson library only.
* [client](client) is a ReactJS application that provides a UX for working with repertoires. 
* [standalone](standalone) is a self-contained web server that serves client and server components for a complete solution. It depends on the [shutdownhook toolbox](https://github.com/seanno/shutdownhook/tree/main/toolbox) library.

# building
```
git clone https://github.com/seanno/shutdownhook.git
git clone https://github.com/seanno/vdj
cd vdj/standalone
./fullbuild.sh
```
  
