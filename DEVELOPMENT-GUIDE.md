# Development Guide

## Technology Stack

The following stack is used when developing Codekvast (in alphabetical order):

1. Angular 4
1. AspectJ (in Load-Time Weaving mode)
1. Docker 1.10.3+ and Docker Compose 1.6.2+ (For running MariaDB and Codekvast Warehouse)
1. Github
1. Gradle 
1. H2 database (disk persistent data, server embedded in Codekvast Daemon)
1. Inkscape (SVG graphics)
1. Java 8
1. Lombok
1. MariaDB 10+ (Codekvast Warehouse)
1. NodeJS
1. Node Package Manager (npm)
1. PhantomJS
1. Spring Boot
1. TypeScript
1. Webpack

## Directory structure

The product itself lives under `product/`.

Sample projects to use when testing Codekvast lives under `sample/`.

GitHub pages (i.e., http://codekvast.crisp.se) lives under the Git branch `gh-pages`.

Development tools live under `tools/`.

## Development environment

There is a Bash script that prepares the development environment.

It works for Ubuntu, and is called `tools/prepare-workstation/run.sh`.
It uses Ansible for setting up the workstation so that it works for Codekvast.

If you run some other OS or prefer to do it by hand, here are the requirements:

### JDK and Node.js

Java 8 is required. OpenJDK is recommended.

Node.js 6, NPM 3.10+ and PhantomJS are required.

Use the following command to install OpenJDK 8, Node.js, npm and PhantomJS (Ubuntu, Debian):

    curl -sL https://deb.nodesource.com/setup_6.x | sudo -E bash -
    sudo apt-get install openjdk-8-jdk openjdk-8-doc openjdk-8-source nodejs
    sudo npm install -g phantomjs-prebuilt

You also must define the environment variable `PHANTOMJS_BIN` to point to the phantomjs executable.
(This is due to a bug in the karma-phantomjs-launcher, which does not use PATH.)

Put this into your `/etc/profile.d/phantomjs.sh` or your `$HOME/.profile` or similar:

    export PHANTOMJS_BIN=$(which phantomjs)
    
### TypeScript

The Codekvast Warehouse web UI is developed with TypeScript and Angular 4. Twitter Bootstrap is used as CSS framework.

npm is used for managing the frontend development environment. Webpack is used as frontend bundler.
    
### Docker Engine & Docker Compose

Docker Engine 1.10 or later and Docker Compose 1.6 or later is required for Codekvast Warehouse.

Install [Docker Engine 1.10.3+](https://docs.docker.com/engine/installation/) and [Docker Compose 1.6.2+](https://docs.docker.com/compose/install/) using
the official instructions.

### Inkscape

Graphics including the Codekvast logo is crafted in SVG format, and exported to PNG in various variants and sizes.
Inkscape is an excellent, free and cross-platform SVG editor.

### Build tool

Codekvast uses **Gradle** as build tool. It uses the Gradle Wrapper, `gradlew`, which is checked in at the root of the workspace.
There is the convenience script `tools/src/script/gradle` which simplifies invocation of gradlew. Install that script in your PATH
(e.g., /usr/local/bin) and use `gradle` instead of `path/to/gradlew`

## Continuous Integration

Codekvast is built by Jenkins at http://jenkins.crisp.se on every push, to all branches.

The pipeline is defined by `Jenkinsfile`.

To access http://jenkins.crisp.se you need to be either a Member or an Outside collaborator of https://github.com/orgs/crispab/people.

## Software publishing
Codekvast binaries are published to Bintray and to Docker Hub.

You execute the publishing to both Bintray and Docker Hub by executing `tools/ship-it.sh` in the root of the project.

Preconditions:

1. Clean workspace (no work in progress).
1. On the master branch.
1. Synced with origin (pushed and pulled).
1. Bintray credentials either in environment variables `BINTRAY_USER` and `BINTRAY_KEY` or as values in in  `~/.gradle/gradle.properties`: 
    
    `bintrayUser=my-bintray-user`
    
    `bintrayKey=my-bintray-key`
    
1. `my-bintray-user` must be member of the Crisp organisation at Bintray.
1. Logged in to Docker Hub and member of the crisp organisation.

### IDE

**Intellij Ultimate Edition 2017+** is the recommended IDE with the following plugins:

1. **Lombok Support** (required)
1. Angular 2 TypeScript Live Templates (optional)
1. JavaScript Support (optional)
1. Karma (optional)
1. Git (optional)
1. Github (optional)
1. Docker (optional)

Do like this to open Codekvast in Intellij the first time:

1. File -> New -> Project from Existing Sources...
1. Navigate to the project root
1. Import project from external model...
1. Select Gradle
1. Click Next
1. Accept the defaults (use the project's Gradle wrapper)
1. Click Finish

After the import, some settings must be changed:

1. File > Settings...
1. Build, Execution, Deployment > Compiler > Annotation Processing
1. Check **Enable annotation processing**
1. Click OK

## Code Style

### Java

The general editor config for IDEA is stored in `.editorconfig`.
If you use some other IDE, please make sure to format the code in format as close to this as possible.

Most important rules:

1. **INDENT WITH SPACES**!
1. Indentation: 4 spaces
1. Line length: 140
1. Charset: UTF-8

### TypeScript

The formatting of TypeScript is described and enforced by tslint.js files in the projects that use TypeScript.
IDEA will automatically pick up and apply these settings when found.

## How to do semi-manual end-to-end tests

All of the non-trivial code is covered with unit tests.

Some tricky integrations are covered by proper integration tests where the external part is executing in Docker containers managed by the tests. 

There is also a smoke test that launches MariaDB and Codekvast Warehouse by means of Docker Compose, and executes some Web Driver tests.
This is just a smoke test though.

To assist manual e2e tests, there is a number of sample apps that are managed by Gradle. They are configured to start with the latest
Codekvast collector attached.

### How to set up for doing development with live data flowing

1. Launch 5 terminal windows
1. In terminal #1 do `./gradlew :sample:jenkins1:run`. This will download and start one version of Jenkins with Codekvast attached.
1. In terminal #2 do `./gradlew :sample:jenkins2:run`. This will download and start another version of Jenkins with Codekvast attached.
1. In terminal #3 do `./gradlew :sample:sample-ltw:run`. This will launch the short-lived sample.app.SampleApp.
1. In terminal #4 do `./gradlew :product:agent:daemon:bootRun`. This will start Codekvast Daemon that will make an inventory of the sample applications.
It will also periodically produce data files to be consumed by the warehouse.
1. In terminal #5 do `./gradlew :product:warehouse:bootRun`. This will start Codekvast Warehouse that will consume the data files produced
 by the daemon.
1. Open a web browser at http://localhost:8080. It will show the warehouse web interface.

### How to do rapid development of the web app

In addition to the above do this:

1. Launch a terminal window
1. `cd product/warehouse/src/webapp`
1. `npm start`. It will start an embedded web server on port 8088.
It reloads changes to the webapp automatically.
1. Open the web browser at http://localhost:8088

### Canned REST responses for off-line webapp development

When running the webapp from `npm start` there is a number of canned REST responses available.
This makes it possible to develop the webapp with nothing else than `npm start` running.

The canned responses are really handy when doing CSS & HTML development, where live data is not necessary.
 
#### End-point /api/v1/methods

In the Methods page, the canned response is delivered from disk by searching for the signature `-----` (five dashes).
 
#### Updating the canned responses

Canned responses has to be re-captured every time the warehouse REST API has been changed.

The canned response for `/api/v1/methods` is captured by executing

    curl -X GET --header 'Accept: application/json' 'http://localhost:8080/api/v1/methods?signature=%25&maxResults=100'|jq . > product/warehouse/src/webapp/src/app/test/canned/v1/MethodData.json
    
from the root directory while `./gradlew :product:warehouse:bootRun` is running.
When doing the capture, make sure that data from the three above mentioned sample apps is stored in the warehouse.

(The JSON response is piped through `jq .` to make it more pretty for the human eye.)
