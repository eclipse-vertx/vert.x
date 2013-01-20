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

package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class RouteMatcher implements Handler<HttpServerRequest> {

  private static final Logger log = LoggerFactory.getLogger(RouteMatcher.class);

  private List<PatternBinding> getBindings = new ArrayList<>();
  private List<PatternBinding> putBindings = new ArrayList<>();
  private List<PatternBinding> postBindings = new ArrayList<>();
  private List<PatternBinding> deleteBindings = new ArrayList<>();
  private List<PatternBinding> optionsBindings = new ArrayList<>();
  private List<PatternBinding> headBindings = new ArrayList<>();
  private List<PatternBinding> traceBindings = new ArrayList<>();
  private List<PatternBinding> connectBindings = new ArrayList<>();
  private List<PatternBinding> patchBindings = new ArrayList<>();
  private Handler<HttpServerRequest> noMatchHandler;

  @Override
  public void handle(HttpServerRequest request) {
    switch (request.method) {
      case "GET":
        route(request, getBindings);
        break;
      case "PUT":
        route(request, putBindings);
        break;
      case "POST":
        route(request, postBindings);
        break;
      case "DELETE":
        route(request, deleteBindings);
        break;
      case "OPTIONS":
        route(request, optionsBindings);
        break;
      case "HEAD":
        route(request, headBindings);
        break;
      case "TRACE":
        route(request, traceBindings);
        break;
      case "PATCH":
        route(request, patchBindings);
        break;
      case "CONNECT":
        route(request, connectBindings);
        break;
    }
  }

  /**
   * Specify a handler that will be called for a matching HTTP GET
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher get(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, getBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP PUT
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher put(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, putBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP POST
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher post(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, postBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP DELETE
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher delete(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, deleteBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP OPTIONS
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher options(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, optionsBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP HEAD
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher head(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, headBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP TRACE
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher trace(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, traceBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP CONNECT
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher connect(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, connectBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP PATCH
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher patch(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, patchBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for all HTTP methods
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher all(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, getBindings);
    addPattern(pattern, handler, putBindings);
    addPattern(pattern, handler, postBindings);
    addPattern(pattern, handler, deleteBindings);
    addPattern(pattern, handler, optionsBindings);
    addPattern(pattern, handler, headBindings);
    addPattern(pattern, handler, traceBindings);
    addPattern(pattern, handler, connectBindings);
    addPattern(pattern, handler, patchBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP GET
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher getWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, getBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP PUT
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher putWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, putBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP POST
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher postWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, postBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP DELETE
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher deleteWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, deleteBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP OPTIONS
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher optionsWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, optionsBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP HEAD
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher headWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, headBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP TRACE
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher traceWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, traceBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP CONNECT
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher connectWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, connectBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP PATCH
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher patchWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, patchBindings);
    return this;
  }

  /**
   * Specify a handler that will be called for all HTTP methods
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher allWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, getBindings);
    addRegEx(regex, handler, putBindings);
    addRegEx(regex, handler, postBindings);
    addRegEx(regex, handler, deleteBindings);
    addRegEx(regex, handler, optionsBindings);
    addRegEx(regex, handler, headBindings);
    addRegEx(regex, handler, traceBindings);
    addRegEx(regex, handler, connectBindings);
    addRegEx(regex, handler, patchBindings);
    return this;
  }

  /**
   * Specify a handler that will be called when no other handlers match.
   * If this handler is not specified default behaviour is to return a 404
   * @param handler
   */
  public RouteMatcher noMatch(Handler<HttpServerRequest> handler) {
    noMatchHandler = handler;
    return this;
  }


  private void addPattern(String input, Handler<HttpServerRequest> handler, List<PatternBinding> bindings) {
    // We need to search for any :<token name> tokens in the String and replace them with named capture groups
    Matcher m =  Pattern.compile(":([A-Za-z][A-Za-z0-9]*)").matcher(input);
    StringBuffer sb = new StringBuffer();
    Set<String> groups = new HashSet<>();
    while (m.find()) {
      String group = m.group().substring(1);
      if (groups.contains(group)) {
        throw new IllegalArgumentException("Cannot use identifier " + group + " more than once in pattern string");
      }
      m.appendReplacement(sb, "(?<$1>[^\\/]+)");
      groups.add(group);
    }
    m.appendTail(sb);
    String regex = sb.toString();
    PatternBinding binding = new PatternBinding(Pattern.compile(regex), groups, handler);
    bindings.add(binding);
  }

  private void addRegEx(String input, Handler<HttpServerRequest> handler, List<PatternBinding> bindings) {
    PatternBinding binding = new PatternBinding(Pattern.compile(input), null, handler);
    bindings.add(binding);
  }

  private void route(HttpServerRequest request, List<PatternBinding> bindings) {
    for (PatternBinding binding: bindings) {
      Matcher m = binding.pattern.matcher(request.path);
      if (m.matches()) {
        Map<String, String> params = new HashMap<>(m.groupCount());
        if (binding.paramNames != null) {
          // Named params
          for (String param: binding.paramNames) {
            params.put(param, m.group(param));
          }
        } else {
          // Un-named params
          for (int i = 0; i < m.groupCount(); i++) {
            params.put("param" + i, m.group(i + 1));
          }
        }
        request.params().putAll(params);
        binding.handler.handle(request);
        return;
      }
    }
    if (noMatchHandler != null) {
      noMatchHandler.handle(request);
    } else {
      // Default 404
      request.response.statusCode = 404;
      request.response.end();
    }
  }

  private static class PatternBinding {
    final Pattern pattern;
    final Handler<HttpServerRequest> handler;
    final Set<String> paramNames;

    private PatternBinding(Pattern pattern, Set<String> paramNames, Handler<HttpServerRequest> handler) {
      this.pattern = pattern;
      this.paramNames = paramNames;
      this.handler = handler;
    }
  }

}
