FROM openjdk:8-jre

MAINTAINER info@tango-controls.org

ENV REST_SERVER_VERSION=rest-server-1.3-SNAPSHOT.jar

ENV REST_API_PORT=10001

RUN useradd -ms /bin/bash rest

WORKDIR /home/rest

COPY target/${REST_SERVER_VERSION}.jar ./

EXPOSE ${REST_API_PORT}

ENTRYPOINT java -DTANGO_HOST=${TANGO_HOST} -DTOMCAT_PORT=${REST_API_PORT} -DTANGO_ACCESS=${TANGO_ACCESS} -jar ${REST_SERVER_VERSION}.jar -nodb -dlist sys/rest/0