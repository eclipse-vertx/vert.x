[![Build Status](https://travis-ci.org/eclipse-vertx/vert.x.svg?branch=master)](https://travis-ci.org/eclipse-vertx/vert.x)

## Vert.x Core

This is the repository for Vert.x core.

Vert.x core contains fairly low-level functionality, including support for HTTP, TCP, file system access, and various other features. You can use this directly in your own applications, and it's used by many of the other components of Vert.x.

For more information on Vert.x and where Vert.x core fits into the big picture please see the [website](http://vertx.io).

## Building Vert.x artifacts

```
> mvn package
```

## Running tests

Runs the tests

```
> mvn test
```

Vert.x supports native transport on BSD and Linux, to run the tests with native transport

```
> mvn test -PtestNativeTransport
```

Vert.x supports domain sockets on Linux exclusively, to run the tests with domain sockets

```
> mvn test -PtestDomainSockets
```

Vert.x has a few integrations tests that run a differently configured JVM (classpath, system properties, etc....)
for ALPN, native and logging

```
> vertx verify -Dtest=FooTest # FooTest does not exists, its only purpose is to execute no tests during the test phase
```

## Building documentation

```
> mvn package -Pdocs -DskipTests
```

Open _target/docs/vertx-core/java/index.html_ with your browser


