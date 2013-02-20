# Building Vert.x

The build uses Gradle. The build script will automatically install the bits it needs if they aren't already installed.

### Prerequisites


## On *nix

Use `./mk`

`./mk tasks` to show the list of tasks

## On Windows

Use `wmk.bat`

`wmk.bat tasks` to show list of tasks


## Maven Central

The JAR components of vert.x can be prepared for Maven repositories and installed locally or uploaded to Maven Central.

To install the JARs into your local Maven cache (~/.m2) execute the command below.  (This is effectively the same as uploading and the refreshing the dependencies of your build.)

    ./mk install


A committer with appropriate access to the `org.vert-x` group at OSS Sonatype (Maven Central) can upload JARs by configuring the following properties in the file `~/.gradle/gradle.properties`.  Gradle uses two plugins to do this, the `signing` and `maven` plugins.

    signing.keyId=12345678
    signing.password=YYYYYYYYY
    signing.secretKeyRingFile=/Users/<username>/.gnupg/secring.gpg
    sonatypeUsername=myusername
    sonatypePassword=XXXXXXXXX

To sign, hash and upload the JARs to Maven Central execute:

    ./mk uploadArchives


## Eclipse IDE

The current build contains a cyclic dependency between the compile and testCompile phases of the vertx-core and vertx-platform projects.

While Gradle resolves this satisfactorily, Eclipse issues an error.  To change this to a warning, change the preference to a warning:

 Preferences->Java->Compiler->Building->Build path problems->Circular dependencies










