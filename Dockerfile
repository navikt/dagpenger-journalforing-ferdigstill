FROM navikt/java:11

COPY build/libs/*-all.jar app.jar
COPY init-scripts /init-scripts
