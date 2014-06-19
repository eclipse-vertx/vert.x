# Building Vert.x

### Prerequisites

Maven 3.2.x

## To build a distro

mvn install -Pdist

The distribution will be under vertx-dist/target/vert.x-<version>

## To run tests

mvn test

## To run a specific test

mvn test -Dtest=<test_name/pattern>



