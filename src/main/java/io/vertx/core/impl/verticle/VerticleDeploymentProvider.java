package io.vertx.core.impl.verticle;

import io.vertx.core.*;
import io.vertx.core.impl.ContextInternal;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class VerticleDeploymentProvider implements Callable<Deployment> {

  final Callable<Verticle> provider;
  final Map<Verticle, Deployment> deploymentMap = new IdentityHashMap<>();

  public VerticleDeploymentProvider(Callable<Verticle> provider) {
    this.provider = provider;
  }

  @Override
  public Deployment call() throws Exception {
    Verticle verticle = provider.call();
    return deploymentMap.computeIfAbsent(verticle, v -> new Deployment() {
      @Override
      public Future<?> start(Context context) throws Exception {
        ContextInternal ci = (ContextInternal) context;
        Promise<Void> promise = ci.promise();
        v.init(context.owner(), context);
        v.start(promise);
        return promise.future();
      }

      @Override
      public Future<?> stop(Context context) throws Exception {
        ContextInternal ci = (ContextInternal) context;
        Promise<Void> promise = ci.promise();
        v.stop(promise);
        return promise.future();
      }
    });
  }
}
