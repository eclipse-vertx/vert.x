## Microbenchmarks

A few JMH benchmarks are available to measure performance of some Vert.x sensistive parts.

The benchmarks are executed with biased locking enabled.

The benchmarks configures a JMH executor that uses `VertxThread`.

Benchmarks are located in the `src/test/benchmarks` folder.

Note that these benchmarks have been added to quickly see the impact of changes but do not replace real benchmarking
done in a lab.

### How-to

Package the JMH microbenchmarks

```
> mvn package -DskipTests -Pbenchmarks
```

Run them all

```
> java -jar target/vertx-core-$VERSION-benchmarks.jar
```

### HttpHeaders benchmarks

- `HeadersEncodeBenchmark`: encode HttpHeaders
- `HeadersContainsBenchmarkv: HttpHeaders contains method
- `HeadersSetBenchmark`: HttpHeaders set method

```
> java -jar target/vertx-core-$VERSION-benchmarks.jar HeadersEncodeBenchmark
```

### HttpServer handler benchmarks

The `HttpServerHandlerBenchmark` benchmarks the `HttpServer` that deals with `HttpRequest` and `HttpResponse`
 objects. It mainly compares a simple  _hello world_ application written with Vert.x and Netty.

```
> java -jar target/vertx-core-$VERSION-benchmarks.jar HttpServerHandlerBenchmark
```

### Context benchmarks

The `RunOnContextBenchmark` measures the impact of the disabling thread checks, context timing that are done
when running Vert.x context tasks.
