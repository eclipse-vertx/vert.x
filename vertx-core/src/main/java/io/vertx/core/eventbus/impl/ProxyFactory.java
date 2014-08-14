/*
 * Copyright 2014 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.Registration;
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

  public <T> Registration registerService(T service, String address) {
    return eventBus.registerHandler(address, (Message<JsonObject> msg) -> {
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
      eventBus.sendWithOptions(address, msg, DeliveryOptions.options().setSendTimeout(proxyOperationTimeout), ar -> {
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
