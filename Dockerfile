FROM navikt/java:11

COPY build/libs/*.jar app.jar
COPY init-scripts /init-scripts
