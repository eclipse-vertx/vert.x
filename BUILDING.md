# Building Vert.x

### Prerequisites

* JDK 1.8+

### Regular build

```
./mvnw clean install
```

### To run tests

```
./mvnw test
```

To run a specific test

```
./mvnw test -Dtest=<test_name/pattern>
```

### Coverage build

To collect code coverage data, you need to launch the build with:

```
./mvnw clean org.jacoco:jacoco-maven-plugin:prepare-agent package -Pcoverage
```

### Imports in IDE

To import the sources in your IDE, just import the project as a Maven project. However, check that:

* `src/main/generated` is marked as a source directory
* `target/generated-test-sources/test-annotations` is marked as a test source directory

This second directory is generated during the `test-compile` phase:

```
./mvnw test-compile
```

### To build a distro

See the Vert.x stack project: https://github.com/vert-x3/vertx-stack and more specifically the `stack-manager`.
