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
import org.vertx.java.core.http.RouteMatcher as JRouteMatcher

/**
 * This class allows you to do route requests based on the HTTP verb and the request URI, in a manner similar
 * to <a href="http://www.sinatrarb.com/">Sinatra</a> or <a href="http://expressjs.com/">Express</a>.<p>
 * RouteMatcher also lets you extract paramaters from the request URI either a simple pattern or using
 * regular expressions for more complex matches. Any parameters extracted will be added to the requests parameters
 * which will be available to you in your request handler.<p>
 * It's particularly useful when writing REST-ful web applications.<p>
 * To use a simple pattern to extract parameters simply prefix the parameter name in the pattern with a ':' (colon).<p>
 * Different handlers can be specified for each of the HTTP verbs, GET, POST, PUT, DELETE etc.<p>
 * For more complex matches regular expressions can be used in the pattern. When regular expressions are used, the extracted
 * parameters do not have a name, so they are put into the HTTP request with names of param0, param1, param2 etc.<p>
 * Multiple matches can be specified for each HTTP verb. In the case there are more than one matching patterns for
 * a particular request, the first matching one will be used.<p>
 * Instances of this class are not thread-safe<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class RouteMatcher {

  private final JRouteMatcher jRM = new JRouteMatcher()

  /**
   * Specify a handler that will be called for a matching HTTP GET
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void get(String pattern, Closure handler) {
    jRM.get(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP PUT
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void put(String pattern, Closure handler) {
    jRM.put(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP POST
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void post(String pattern, Closure handler) {
    jRM.post(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP DELETE
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void delete(String pattern, Closure handler) {
    jRM.delete(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP OPTIONS
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void options(String pattern, Closure handler) {
    jRM.options(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP HEAD
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void head(String pattern, Closure handler) {
    jRM.head(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP TRACE
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void trace(String pattern, Closure handler) {
    jRM.trace(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP CONNECT
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void connect(String pattern, Closure handler) {
    jRM.connect(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP PATCH
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void patch(String pattern, Closure handler) {
    jRM.patch(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for all HTTP methods
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  void all(String pattern, Closure handler) {
    jRM.all(pattern, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP GET
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void getWithRegEx(String regex, Closure handler) {
    jRM.getWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP PUT
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void putWithRegEx(String regex, Closure handler) {
    jRM.putWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP POST
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void postWithRegEx(String regex, Closure handler) {
    jRM.postWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP DELETE
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void deleteWithRegEx(String regex, Closure handler) {
    jRM.deleteWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP OPTIONS
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void optionsWithRegEx(String regex, Closure handler) {
    jRM.optionsWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP HEAD
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void headWithRegEx(String regex, Closure handler) {
    jRM.headWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP TRACE
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void traceWithRegEx(String regex, Closure handler) {
    jRM.traceWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP CONNECT
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void connectWithRegEx(String regex, Closure handler) {
    jRM.connectWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for a matching HTTP PATCH
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void patchWithRegEx(String regex, Closure handler) {
    jRM.patchWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called for all HTTP methods
   * @param regex A regular expression
   * @param handler The handler to call
   */
  void allWithRegEx(String regex, Closure handler) {
    jRM.allWithRegEx(regex, wrapHandler(handler))
  }

  /**
   * Specify a handler that will be called when no other handlers match.
   * If this handler is not specified default behaviour is to return a 404
   * @param handler
   */
  void noMatch(Closure handler) {
    jRM.noMatch(wrapHandler(handler))    
  }

  /**
   * @return Convert to a Closure so can be passed to (e.g.) HttpServer requestHandler
   */
  Closure asClosure() {
    return {
      jRM.handle(it.toJavaRequest())
    }
  }

  private Handler wrapHandler(Closure handler) {
    return {handler(new HttpServerRequest(it))} as Handler
  }



}
