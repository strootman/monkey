FROM discoenv/clojure-base:master

ENV CONF_TEMPLATE=/usr/src/app/monkey.properties.tmpl
ENV CONF_FILENAME=monkey.properties
ENV PROGRAM=monkey

VOLUME ["/etc/iplant/de"]

COPY project.clj /usr/src/app/
RUN lein deps

COPY conf/main/logback.xml /usr/src/app/
COPY . /usr/src/app

RUN lein uberjar && \
    cp target/monkey-standalone.jar .

RUN ln -s "/usr/bin/java" "/bin/monkey"

ENTRYPOINT ["run-service", "-Dlogback.configurationFile=/etc/iplant/de/logging/monkey-logging.xml", "-cp", ".:monkey-standalone.jar", "monkey.core"]

ARG git_commit=unknown
ARG version=unknown
ARG descriptive_version=unknown

LABEL org.cyverse.git-ref="$git_commit"
LABEL org.cyverse.version="$version"
LABEL org.cyverse.descriptive-version="$descriptive_version"
