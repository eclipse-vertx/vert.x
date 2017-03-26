## Vert.x Core

This is the repository for Vert.x core.

Vert.x core contains fairly low-level functionality, including support for HTTP, TCP, file system access, and various other features. You can use this directly in your own applications, and it's used by many of the other components of Vert.x.

For more information on Vert.x and where Vert.x core fits into the big picture please see the [website](http://vertx.io).

## Building Vert.x artifacts

```
> mvn package
```

## Building documentation

```
> mvn package -Pdocs -DskipTests
```

Open _target/docs/vertx-core/java/index.html_ with your browser
