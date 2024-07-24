
###
### VDJ 
###

FROM eclipse-temurin:21

RUN mkdir /opt/vdj
COPY target/vdj-standalone-1.0-SNAPSHOT.jar /opt/vdj
COPY docker/config-docker.json /opt/vdj
COPY docker/logging-docker.json /opt/vdj

EXPOSE 3000

CMD ["java", "-cp", "/opt/vdj/vdj-standalone-1.0-SNAPSHOT.jar", "com.shutdownhook.vdj.standalone.App", "/opt/vdj/config-docker.json"]




