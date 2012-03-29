/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package org.vertx.groovy.core.http

import org.vertx.java.core.Handler

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class RouteMatcher extends org.vertx.java.core.http.RouteMatcher {

  private final org.vertx.java.core.http.RouteMatcher jRM = new org.vertx.java.core.http.RouteMatcher()

  void get(String pattern, Closure handler) {
    super.get(pattern, wrapHandler(handler))
  }

  
  void put(String pattern, Closure handler) {
    super.put(pattern, wrapHandler(handler))
  }

  
  void post(String pattern, Closure handler) {
    super.post(pattern, wrapHandler(handler))
  }

  
  void delete(String pattern, Closure handler) {
    super.delete(pattern, wrapHandler(handler))
  }

  
  void options(String pattern, Closure handler) {
    super.options(pattern, wrapHandler(handler))
  }

  
  void head(String pattern, Closure handler) {
    super.head(pattern, wrapHandler(handler))
  }

  
  void trace(String pattern, Closure handler) {
    super.trace(pattern, wrapHandler(handler))
  }

  
  void connect(String pattern, Closure handler) {
    super.connect(pattern, wrapHandler(handler))
  }

  
  void patch(String pattern, Closure handler) {
    super.patch(pattern, wrapHandler(handler))
  }

  
  void all(String pattern, Closure handler) {
    super.all(pattern, wrapHandler(handler))
  }

  
  void getWithRegEx(String regex, Closure handler) {
    super.getWithRegEx(regex, wrapHandler(handler))
  }

  
  void putWithRegEx(String regex, Closure handler) {
    super.putWithRegEx(regex, wrapHandler(handler))
  }

  
  void postWithRegEx(String regex, Closure handler) {
    super.postWithRegEx(regex, wrapHandler(handler))
  }

  
  void deleteWithRegEx(String regex, Closure handler) {
    super.deleteWithRegEx(regex, wrapHandler(handler))
  }

  
  void optionsWithRegEx(String regex, Closure handler) {
    super.optionsWithRegEx(regex, wrapHandler(handler))
  }

  
  void headWithRegEx(String regex, Closure handler) {
    super.headWithRegEx(regex, wrapHandler(handler))
  }

  
  void traceWithRegEx(String regex, Closure handler) {
    super.traceWithRegEx(regex, wrapHandler(handler))
  }

  
  void connectWithRegEx(String regex, Closure handler) {
    super.connectWithRegEx(regex, wrapHandler(handler))
  }

  
  void patchWithRegEx(String regex, Closure handler) {
    super.patchWithRegEx(regex, wrapHandler(handler))
  }

  
  void allWithRegEx(String regex, Closure handler) {
    super.allWithRegEx(regex, wrapHandler(handler))
  }

  
  void noMatch(Closure handler) {
    super.noMatch(handler)    
  }

  Closure asClosure() {
    return { jRM.handle(it.toJavaRequest())}
  }

  private def wrapHandler(Closure handler) {
    return {handler.call(new HttpServerRequest(it))} as Handler
  }



}
