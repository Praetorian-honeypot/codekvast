# Codekvast Release Notes


## 0.11.2

1. UI and database schema change; renamed application_statistics.num_probably_dead to num_possibly_dead.
 Database is migrated by Flyway.

## 0.11.1

1. Simplified logback.xml
1. Improved collectors.html so it now shows warning color for dead collectors.

## 0.11.0

1. Keeps track of agent clock skew. Breaks compatibility of agent-API.

## 0.10.3

1. Server now tracks uptime.

## 0.10.2

1. Added Data Age to collectors.html.
1. Added version info to codekvast-server web page footer.
1. Added progress bar to statistics Usage Cycle column.

## 0.10.1

1. Added Redhat 6 startscript for codekvast-agent.

## 0.10.0

1. Changed format of JvmData. Breaks compatibility between collector, agent and server.

## 0.9.5

1. Bug fix in codekvast-agent: Signature.millisSinceJvmStart was not correct.
1. Implemented server-side support for application statistics.

## 0.9.4

1. codekvast-server now requires JDK 8.

## 0.9.3

1. Java compiler for 1.6 & 1.7 uses correct bootclasspath.

## 0.9.2

1. Now excludes trivial methods from tracking (equals(), hashCode(), toString(), compareTo(), getters, setters).

## 0.9.1

1. Added a Gentoo start script for codekvast-server and codekvast-agent.

## 0.9.0

1. Improved the installation procedures in CodekvastUserManual.
1. Now CodekvastUserManual.html is self-contained. No more external images.
1. codekvast-agent now runs on Java 6.

## 0.8.18

1. Improved the server web interface.
1. codekvast-agent now uses a private H2 database for storing not yet uploaded data.

## 0.8.17

1. Added /etc/init.d scripts for agent and server.
1. Documented installation of agent and server (work in progress).
