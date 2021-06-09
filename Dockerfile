FROM folioci/alpine-jre-openjdk11:latest
MAINTAINER Ian.Ibbotson@k-int.com
VOLUME /tmp
ENV VERTICLE_FILE mod-service-interaction.war
ENV VERTICLE_HOME /
COPY service/build/libs/mod-service-interaction-*.*.*.jar mod-service-interaction.war
EXPOSE 8080/tcp
