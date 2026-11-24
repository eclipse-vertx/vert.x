# Asynchronous tests

Practices for writing and reviewing vertx-core tests that run with `io.vertx.test.core.VertxRunner`
(`VertxTestBase2` subclasses, `HttpTestBase2`, `SimpleHttpTest2`, or `@RunWith(VertxRunner.class)`).

## The runner

- A test method declares `io.vertx.test.core.Checkpoint` parameters; the runner waits until each one is succeeded
  or failed. `checkpoint.awaitSuccess()` waits for one mid-test, `checkpoint.asLatch(n)` turns it into a latch for
  repeated completions.
- A checkpoint completes once: keep one per expected completion.
- An exception reported on a context fails the checkpoints of the test, but the runner does not interrupt the test
  thread: a test blocked in `await()` stays blocked until it times out.

## API

`io.vertx.test.core.FutureAssert` (static import `FutureAssert.assertThat`) is a `Future` describing the expected
outcome of another future:

- `assertThat(future)` wraps a future.
- `isFailure()` expects a failure and continues with the cause, an unexpected success fails with an `AssertionError`.
- `asInstanceOf(Class<U>)` checks the type of the value and narrows to it.
- `verify(Checkpoint)` reports the outcome to a checkpoint, `await()` waits for it on the test thread.
- Every `Future` operator returns a `FutureAssert`, and a `FutureAssert` can be returned from any operator lambda.

`Future#peek(Handler)` calls the handler with the value of a succeeded future, a failure thrown by the handler fails
the returned future: the place for assertions in a chain.

`ContextInternal#future(Handler<Promise<T>>)` runs the handler on the context and returns the future of the promise:
the way to run an operation bound to a context and wait for its outcome.

Nothing is checked until the outcome is observed: every chain ends with `await()` on the test thread, or with
`verify(checkpoint)` / `onComplete(checkpoint)` elsewhere.

## Order of preference

1. Outcome observed on the test thread: `await()`, without a checkpoint.

   ```java
   // before
   public void test(Checkpoint checkpoint) {
     client.connect(options).onComplete(TestUtils.onFailure(err -> checkpoint.succeed()));
   }
   // after
   public void test() {
     assertThat(client.connect(options)).isFailure().await();
   }
   ```

   Assertions on the awaited value or failure are plain statements after `await()`.
   When later statements drive the future (sending data, completing a promise), keep it in a local variable and await
   it after them. Futures collected in a loop are awaited with `Future.all(list).await()`; a fixed number of futures
   is awaited one statement each.

2. Outcome observed off the test thread (server handler, callback): connect the chain to a checkpoint.

   ```java
   // before
   stream.write(chunk).onComplete(ar -> {
     assertTrue(ar.failed());
     checkpoint.succeed();
   });
   // after
   assertThat(stream.write(chunk))
     .isFailure()
     .verify(checkpoint);
   ```

   Inside a chain, side outcomes the chain does not depend on get one checkpoint each, rather than a `Future.all`
   returned by the chain.

3. Expected failures:

   ```java
   // before
   try {
     future.await();
     fail();
   } catch (ConnectException e) {
     assertEquals("...", e.getMessage());
   }
   // after
   ConnectException e = assertThat(future)
     .isFailure()
     .asInstanceOf(ConnectException.class)
     .await();
   assertEquals("...", e.getMessage());
   ```

   `assertThrows(X.class, future::await)` follows the same shape. Exact class checks stay exact
   (`assertEquals(X.class, err.getClass())`). A chain ending with `isFailure()` accepts the failure of every step:
   narrow it with `asInstanceOf(...)`, or keep the other steps out of the chain.

4. Callback pyramids: flatten them with `compose`, one chain ending with a single `await()` or checkpoint. Do not
   wrap each callback in its own `assertThat(...).peek(...)`: a nested `assertThat` usually means a missed
   composition (parallel branches excepted).

`TestUtils.onSuccess` / `onFailure` are not used.

## HTTP clients

Response operations are composed on `send()` within the request block, and response checks use
`HttpResponseExpectation`:

```java
Buffer body = client
  .request(requestOptions)
  .compose(request -> request
    .send()
    .expecting(HttpResponseExpectation.SC_OK)
    .compose(HttpClientResponse::body))
  .await();
```

- Never call `response()`, `body()`, `end()` or register response handlers after awaiting the request on the test
  thread, nor in a later stage of the outer chain: they may run on another context than the one delivering the
  response, and miss its events.
- Chain on the futures client operations return (`writeHead()`, `write(...)`, `end()`, `send()`) instead of
  dropping them, unless the operation is expected to fail or races with a reset or close.
- `expecting(SC_OK)`, `status(code)`, `JSON`, `contentType(...)` replace `assertEquals(200, resp.statusCode())`; other
  checks go in a `peek`.

## Servers

- A server assertion that throws before the response is sent leaves the client `await()` blocked. The server responds
  in every case:
  - `.andThen(ar -> request.response().end())` when the response ends whatever the outcome, the outcome going to a
    checkpoint;
  - `.transform(ar -> response.setStatusCode(ar.succeeded() ? 200 : 500).end())` and `expecting(SC_OK)` on the
    client, when the server outcome depends on other operations;
  - plain `compose` when a failure means the request stream is broken, the client then fails anyway.
- A server outcome the test must wait for goes to a checkpoint, or to a `Promise` awaited by the test (e.g. a value
  received by an exception handler).
- WebSocket: when the server sends right after the handshake, the client may miss frames. Preferably, the server waits
  for a message sent by the client; when the test is about the server sending first, register the client handlers
  before connecting (`ClientWebSocket ws = client.webSocket(); ws.handler(...); ws.connect(options)`).

## Handlers and helpers

- `peek` holds assertions only. Set-up starting an asynchronous operation belongs in `compose` returning its future;
  `peek` never drops a future.
- Handler callbacks (`handler`, `endHandler`, `closeHandler`, `exceptionHandler`, timers) keep their checkpoints:
  do not turn handlers into futures.
- A callback on a side future (`promise.future().onSuccess(v -> assertTrue(...))`) loses its failure: fold it into the
  chain ending with `await()` or a checkpoint.
- `CompletableFuture`: a vert.x `Promise` when it carries a value or a callback, a `Checkpoint` when it only blocks.
- `Future.future(p -> ...)` instead of a local promise plus return; `context.future(p -> op().onComplete(p))` instead
  of `runOnContext` completing a promise.
- Never `await()` on an event loop, inside a callback or on the context of a verticle, nor on a future that only a
  later statement completes.

## What stays

- Tests whose subject is the future, context or queue mechanics (completion semantics, listener ordering, context
  dispatch, thread confinement) keep plain futures and their shape, without `TestUtils` helpers.
- Shapes where an assertion would throw before a response is sent (if/else reporting to a checkpoint, try/catch around
  a synchronous call) stay.
- Synchronous `assertThrows` and try/catch on non future code stay.

## Style

- Chains are aligned vertically, one call per line; a single call may stay on one line (`future.await()`).
- Classes extending `AsyncTestBase` or statically importing another `assertThat` use `FutureAssert.assertThat(...)`.

## Verification

- `mvn test-compile` and `mvn spotless:check` in vertx-core.
- Tests use fixed ports: never run two test runs at the same time, even in separate checkouts.
- Run the concrete subclasses of an abstract test; run timing-sensitive tests several times.
