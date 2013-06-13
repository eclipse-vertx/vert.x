# Building Vert.x

The build uses Gradle. The build script will automatically install the bits it needs if they aren't already installed.

### Prerequisites


## On *nix

Use `./gradlew`

`./gradlew tasks` to show the list of tasks

## On Windows

Use `gradlew.bat`

`gradlew.bat tasks` to show list of tasks

## To build a distro

    ./gradlew dist

OR

    gradlew.bat dist

## To run tests

    ./gradlew test

OR

    gradlew.bat test

## To run a specific test in Core

    ./gradlew vertx-core:test -Dtest.single=<test_name/pattern>

## To run a specific test in Platform

    ./gradlew vertx-platform:test -Dtest.single=<test_name/pattern>

## Maven Central

The JAR components of vert.x can be prepared for Maven repositories and installed locally or uploaded to Maven Central.

To install the JARs into your local Maven cache (~/.m2) execute the command below.  (This is effectively the same as uploading and the refreshing the dependencies of your build.)

    ./gradlew install


A committer with appropriate access to the `io.vertx` group at OSS Sonatype (Maven Central) can upload JARs by configuring the following properties in the file `~/.gradle/gradle.properties`.  Gradle uses two plugins to do this, the `signing` and `maven` plugins.

    signing.keyId=12345678
    signing.password=YYYYYYYYY
    signing.secretKeyRingFile=/Users/<username>/.gnupg/secring.gpg
    sonatypeUsername=myusername
    sonatypePassword=XXXXXXXXX

To sign, hash and upload the JARs to Maven Central execute:

    ./gradlew uploadArchives








