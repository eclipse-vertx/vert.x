# SSL Configuration
## Reloading SSL
The SSL configuration can be reloaded based on different input types, such as a KeyStore file on the file system, KeyStore InputStream, KeyManager, KeyManagerFactory and so on.
The Vert.x Core library has the capability of accepting a reloadable ssl configuration from a third-party library displayed below:
```xml
<dependency>
    <groupId>io.github.hakky54</groupId>
    <artifactId>sslcontext-kickstart</artifactId>
</dependency>
```
## Usage
Below are two examples of reloading the SSL configuration for a client. Same configuration can be applied for a server.
See here also for a working demo: [Instant Server SSL Reloading with Vert.x](https://github.com/Hakky54/java-tutorials/tree/main/instant-server-ssl-reloading-with-vertx/vertx-server)

### Server
#### Scheduled reload
```java
public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {
        Router router = Router.router(vertx);
        router.route().handler(context -> context.json(
                new JsonObject().put("message", "Hello World!")
        ));

        SSLFactory baseSslFactory = SSLFactory.builder()
                .withDummyIdentityMaterial()
                .withDummyTrustMaterial()
                .withSwappableIdentityMaterial()
                .withSwappableTrustMaterial()
                .build();

        Runnable sslUpdater = () -> {
            SSLFactory updatedSslFactory = SSLFactory.builder()
                    .withIdentityMaterial(Paths.get("/path/to/your/identity.jks"), "password".toCharArray())
                    .withTrustMaterial(Paths.get("/path/to/your/truststore.jks"), "password".toCharArray())
                    .build();

            SSLFactoryUtils.reload(baseSslFactory, updatedSslFactory);
        };

        // initial update of ssl material to replace the dummies
        sslUpdater.run();

        // update ssl material every hour
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(sslUpdater, 1, 1, TimeUnit.HOURS);

        HttpServerOptions serverOptions = new HttpServerOptions()
                .setSsl(true)
                .setClientAuth(ClientAuth.REQUIRED)
                .setKeyCertOptions(KeyCertOptions.wrap(baseSslFactory.getKeyManager().orElseThrow()))
                .setTrustOptions(TrustOptions.wrap(baseSslFactory.getTrustManager().orElseThrow()));

        vertx.createHttpServer(serverOptions)
                .requestHandler(router)
                .listen(8443)
                .onSuccess(server -> System.out.println("HTTP server started on port " + server.actualPort()));
    }

}
```
### WebClient
#### Reload on demand
```java
SSLFactory baseSslFactory = SSLFactory.builder()
  .withIdentityMaterial(Paths.get("/path/to/your/identity.jks"), "password".toCharArray())
  .withTrustMaterial(Paths.get("/path/to/your/truststore.jks"), "password".toCharArray())
  .withSwappableIdentityMaterial()
  .withSwappableTrustMaterial()
  .build();

WebClientOptions clientOptions = new WebClientOptions();

sslFactory.getKeyManager()
  .map(KeyCertOptions::wrap)
  .ifPresent(clientOptions::setKeyCertOptions);

sslFactory.getTrustManager()
  .map(TrustOptions::wrap)
  .ifPresent(clientOptions::setTrustOptions);

WebClient webClient = WebClient.create(Vertx.vertx(), clientOptions);

// After some time whenever needed the code below
// can be executed to reload the ssl configuration

SSLFactory updatedSslFactory = SSLFactory.builder()
  .withIdentityMaterial(Paths.get("/path/to/your/identity.jks"), "password".toCharArray())
  .withTrustMaterial(Paths.get("/path/to/your/truststore.jks"), "password".toCharArray())
  .build();

SSLFactoryUtils.reload(baseSslFactory, updatedSslFactory);
```
#### Scheduled reload
```java
SSLFactory baseSslFactory = SSLFactory.builder()
  .withDummyIdentityMaterial()
  .withDummyTrustMaterial()
  .withSwappableIdentityMaterial()
  .withSwappableTrustMaterial()
  .build();

WebClientOptions clientOptions = new WebClientOptions();

sslFactory.getKeyManager()
  .map(KeyCertOptions::wrap)
  .ifPresent(clientOptions::setKeyCertOptions);

sslFactory.getTrustManager()
  .map(TrustOptions::wrap)
  .ifPresent(clientOptions::setTrustOptions);

WebClient webClient = WebClient.create(Vertx.vertx(), clientOptions);

Runnable sslUpdater = () -> {
  SSLFactory updatedSslFactory = SSLFactory.builder()
    .withIdentityMaterial(Paths.get("/path/to/your/identity.jks"), "password".toCharArray())
    .withTrustMaterial(Paths.get("/path/to/your/truststore.jks"), "password".toCharArray())
    .build();

    SSLFactoryUtils.reload(baseSslFactory, updatedSslFactory);
};

// initial update of ssl material to replace the dummies
sslUpdater.run();

// update ssl material every hour
Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(sslUpdater, 1, 1, TimeUnit.HOURS);
```
