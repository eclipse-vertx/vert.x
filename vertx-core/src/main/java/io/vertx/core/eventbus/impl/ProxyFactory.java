/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ProxyFactory {

  private static final Logger log = LoggerFactory.getLogger(ProxyFactory.class);


  private static final String OPS_FIELD = "operation";
  private static final String ARGS_FIELD = "args";
  private static final int USER_FAILURE = 1;

  private final EventBus eventBus;
  private final long proxyOperationTimeout;

  public ProxyFactory(EventBus eventBus, long proxyOperationTimeout) {
    this.eventBus = eventBus;
    this.proxyOperationTimeout = proxyOperationTimeout;
  }

  public <T> T createProxy(Class<T> clazz, String address) {
    InvocationHandler handler = new ProxyInvocationHandler(address);
    T proxy = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, handler);
    return proxy;
  }

  public <T> MessageConsumer registerService(T service, String address) {
    return eventBus.<JsonObject>consumer(address).handler((Message<JsonObject> msg) -> {
      JsonObject body = msg.body();
      String op = body.getString(OPS_FIELD);
      JsonArray args = body.getArray(ARGS_FIELD);
      Method[] meths = service.getClass().getMethods();
      for (Method meth : meths) {
        if (meth.getName().equals(op)) {
          Class<?>[] paramTypes = meth.getParameterTypes();
          boolean hasHandler = paramTypes.length > 0 && paramTypes[paramTypes.length - 1].equals(Handler.class);
          Object[] oargs = new Object[args.size() + (hasHandler ? 1 : 0)];
          int count = 0;
          for (Object arg : args) {
            oargs[count++] = arg;
          }
          if (hasHandler) {
            oargs[oargs.length - 1] = new Handler<AsyncResult>() {
              @Override
              public void handle(AsyncResult ar) {
                if (ar.failed()) {
                  msg.fail(USER_FAILURE, ar.cause().getMessage());
                } else {
                  msg.reply(ar.result());
                }
              }
            };
          }
          try {
            meth.invoke(service, oargs);
          } catch (Exception e) {
            log.error("Failed to invoke proxy method", e);
          }
        }
      }
    });
  }

  private class ProxyInvocationHandler implements InvocationHandler {

    private String address;

    ProxyInvocationHandler(String address) {
      this.address = address;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

      // Check types - TODO this could be optimised

      if (method.getReturnType() != void.class) {
        throw new VertxException("Proxied methods must have a void return");
      }

      Class<?>[] paramTypes = method.getParameterTypes();
      int pos = 0;
      for (Class<?> clazz: paramTypes) {
         if (clazz.isAssignableFrom(Handler.class)) {
          if (pos != paramTypes.length - 1) {
            throw new VertxException("Handler must be last parameter if specified in proxied method " + method.getName());
          }
        } else if (clazz == Integer.class || clazz == int.class ||
          clazz == Long.class || clazz == long.class ||
          clazz == Short.class || clazz == short.class ||
          clazz == Float.class || clazz == float.class ||
          clazz == Double.class || clazz == double.class ||
          clazz == Boolean.class || clazz == boolean.class ||
          clazz == Byte.class || clazz == byte.class ||
          clazz == String.class ||
          clazz == JsonObject.class ||
          clazz == JsonArray.class) {
          // OK
        } else {
          throw new VertxException("Invalid type " + clazz + " in proxied method " + method.getName());
        }
        pos++;
      }

      JsonObject msg = new JsonObject();
      msg.putString(OPS_FIELD, method.getName());
      JsonArray jargs = new JsonArray();
      Handler handler = null;
      for (Object arg: args) {
        if (arg instanceof Handler) {
          handler = (Handler)arg;
        } else {
          jargs.add(arg);
        }
      }
      msg.putArray(ARGS_FIELD, jargs);
      Handler fHandler = handler;
      eventBus.send(address, msg, new DeliveryOptions().setSendTimeout(proxyOperationTimeout), ar -> {
        if (fHandler != null) {
          if (ar.failed()) {
            fHandler.handle(ar);
          } else {
            fHandler.handle(Future.completedFuture(ar.result().body()));
          }
        }
      });
      return null;
    }
  }

}
