[![Build Status (5.x)](https://github.com/eclipse-vertx/vert.x/actions/workflows/ci-5.x.yml/badge.svg)](https://github.com/eclipse-vertx/vert.x/actions/workflows/ci-5.x.yml)
[![Build Status (4.x)](https://github.com/eclipse-vertx/vert.x/actions/workflows/ci-4.x.yml/badge.svg)](https://github.com/eclipse-vertx/vert.x/actions/workflows/ci-4.x.yml)

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

Tests can be run with specified HTTP port and/or HTTPS port.

```
> mvn test -Dvertx.httpPort=8888 -Dvertx.httpsPort=4044
```

Vert.x supports native transport on BSD and Linux, to run the tests with native transport

```
> mvn test -PNativeEpoll
> mvn test -PNativeIoUring
> mvn test -PNativeKQueue
```

Vert.x supports domain sockets on Linux exclusively, to run the tests with domain sockets

```
> mvn test -PNativeEpoll+DomainSockets
```

Vert.x has integrations tests that run a differently configured JVM (classpath, system properties, etc....)

```
> vertx verify -Dtest=FooTest # FooTest does not exists, its only purpose is to execute no tests during the test phase
```

## Building documentation

```
> mvn package -Pdocs -DskipTests
```

Open _target/docs/vertx-core/java/index.html_ with your browser


