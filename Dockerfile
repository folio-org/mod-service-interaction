FROM folioci/alpine-jre-openjdk21:latest

# Install latest patch versions of packages: https://pythonspeed.com/articles/security-updates-in-docker/
USER root
RUN apk upgrade --no-cache
USER folio

# Copy the Spring Boot fat jar built at the repo root to the container
ENV APP_FILE=mod-service-interaction.jar
ARG JAR_FILE=./target/mod-service-interaction-*.jar
COPY ${JAR_FILE} ${JAVA_APP_DIR}/${APP_FILE}

# Expose this port locally in the container.
EXPOSE 8080
