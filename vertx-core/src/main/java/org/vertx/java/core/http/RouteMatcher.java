/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.core.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.vertx.java.core.Handler;

/**
 * This class allows you to do route requests based on the HTTP verb and the request URI, in a manner similar
 * to <a href="http://www.sinatrarb.com/">Sinatra</a> or <a href="http://expressjs.com/">Express</a>.<p>
 * RouteMatcher also lets you extract parameters from the request URI either a simple pattern or using
 * regular expressions for more complex matches. Any parameters extracted will be added to the requests parameters
 * which will be available to you in your request handler.<p>
 * It's particularly useful when writing REST-ful web applications.<p>
 * To use a simple pattern to extract parameters simply prefix the parameter name in the pattern with a ':' (colon).<p>
 * Different handlers can be specified for each of the HTTP verbs, GET, POST, PUT, DELETE etc.<p>
 * For more complex matches regular expressions can be used in the pattern. When regular expressions are used, the extracted
 * parameters do not have a name, so they are put into the HTTP request with names of param0, param1, param2 etc.<p>
 * Multiple matches can be specified for each HTTP verb. In the case there are more than one matching patterns for
 * a particular request, the first matching one will be used.<p>
 * Instances of this class are not thread-safe except for updating or removing
 * routes after a call to setUpdatable(true). Call setUpdatable(false) when not
 * updating to remove try/finally and {@link ReentrantReadWriteLock.ReadLock}
 * lock/unlock overhead.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Troy Collinsworth
 */
public class RouteMatcher implements Handler<HttpServerRequest> {
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final AtomicBoolean updatable = new AtomicBoolean();
	
  private final List<PatternBinding> getBindings = new ArrayList<>();
  private final List<PatternBinding> putBindings = new ArrayList<>();
  private final List<PatternBinding> postBindings = new ArrayList<>();
  private final List<PatternBinding> deleteBindings = new ArrayList<>();
  private final List<PatternBinding> optionsBindings = new ArrayList<>();
  private final List<PatternBinding> headBindings = new ArrayList<>();
  private final List<PatternBinding> traceBindings = new ArrayList<>();
  private final List<PatternBinding> connectBindings = new ArrayList<>();
  private final List<PatternBinding> patchBindings = new ArrayList<>();
  private Handler<HttpServerRequest> noMatchHandler;

  /**
   * {@inheritDoc}
   * 
   * If the RouteMatcher is not being updated the route matching does not
   * incur any overhead other than checking an AtomicBoolean. If the
   * RouteMatcher is in the updated state, the route matching incurs a
   * try/finally and {@link ReentrantReadWriteLock.ReadLock} lock/unlock
   * overhead. Actual RouteMatcher updates momentarily take a
   * {@link ReentrantReadWriteLock.WriteLock} and very briefly block route
   * matching.
   */
  @Override
  public void handle(HttpServerRequest request) {
    switch (request.method()) {
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
      default:
        notFound(request);
    }
  }

  /**
   * Specify a handler that will be called for a matching HTTP GET
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher get(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, getBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP PUT
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher put(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, putBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP POST
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher post(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, postBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP DELETE
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher delete(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, deleteBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP OPTIONS
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher options(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, optionsBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP HEAD
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher head(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, headBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP TRACE
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher trace(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, traceBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP CONNECT
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher connect(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, connectBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP PATCH
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher patch(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, patchBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for all HTTP methods
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public RouteMatcher all(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, getBindings, lock.writeLock());
    addPattern(pattern, handler, putBindings, lock.writeLock());
    addPattern(pattern, handler, postBindings, lock.writeLock());
    addPattern(pattern, handler, deleteBindings, lock.writeLock());
    addPattern(pattern, handler, optionsBindings, lock.writeLock());
    addPattern(pattern, handler, headBindings, lock.writeLock());
    addPattern(pattern, handler, traceBindings, lock.writeLock());
    addPattern(pattern, handler, connectBindings, lock.writeLock());
    addPattern(pattern, handler, patchBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP GET
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher getWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, getBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP PUT
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher putWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, putBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP POST
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher postWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, postBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP DELETE
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher deleteWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, deleteBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP OPTIONS
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher optionsWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, optionsBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP HEAD
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher headWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, headBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP TRACE
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher traceWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, traceBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP CONNECT
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher connectWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, connectBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for a matching HTTP PATCH
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher patchWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, patchBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called for all HTTP methods
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public RouteMatcher allWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, getBindings, lock.writeLock());
    addRegEx(regex, handler, putBindings, lock.writeLock());
    addRegEx(regex, handler, postBindings, lock.writeLock());
    addRegEx(regex, handler, deleteBindings, lock.writeLock());
    addRegEx(regex, handler, optionsBindings, lock.writeLock());
    addRegEx(regex, handler, headBindings, lock.writeLock());
    addRegEx(regex, handler, traceBindings, lock.writeLock());
    addRegEx(regex, handler, connectBindings, lock.writeLock());
    addRegEx(regex, handler, patchBindings, lock.writeLock());
    return this;
  }

  /**
   * Specify a handler that will be called when no other handlers match.
   * If this handler is not specified default behaviour is to return a 404
   */
  public RouteMatcher noMatch(Handler<HttpServerRequest> handler) {
    noMatchHandler = handler;
    return this;
  }
  
  /**
   * Set true before updating and wait a brief period for current
   * routeMatching to complete. Since routeMatching should be very quick and
   * updates are rare, a second or two should be adequate. When set route
   * matching will be slightly slower due to try/finally and locking overhead.
   * When updates are complete be sure to set updatable to false to remove the
   * try/finally and locking overhead from route matching.
   * 
   * @param updatable
   *            true when safe to update
   */
  public void setUpdatable(boolean updatable) {
    if (updatable) {
      this.updatable.set(updatable);
    } else {
      this.updatable.set(updatable);
    }
  }
  
	/**
	 * Removes all patterns that match for the specified method or all methods
	 * if ALL is specified.
	 * 
	 * @param pattern
	 * @param method
	 *            GET, PUT, POST, DELETE, OPTIONS, HEAD, TRACE, CONNECT, PATCH,
	 *            ALL
	 * @return the number of patterns removed or zero for none
	 */
	public int removePattern(String pattern, String method) {
		int removedCnt = 0;
		switch (method.toUpperCase()) {
		case "GET":
			removedCnt += removeMatchingBindings(pattern, getBindings);
			break;
		case "PUT":
			removedCnt += removeMatchingBindings(pattern, putBindings);
			break;
		case "POST":
			removedCnt += removeMatchingBindings(pattern, postBindings);
			break;
		case "DELETE":
			removedCnt += removeMatchingBindings(pattern, deleteBindings);
			break;
		case "OPTIONS":
			removedCnt += removeMatchingBindings(pattern, optionsBindings);
			break;
		case "HEAD":
			removedCnt += removeMatchingBindings(pattern, headBindings);
			break;
		case "TRACE":
			removedCnt += removeMatchingBindings(pattern, traceBindings);
			break;
		case "PATCH":
			removedCnt += removeMatchingBindings(pattern, patchBindings);
			break;
		case "CONNECT":
			removedCnt += removeMatchingBindings(pattern, connectBindings);
			break;
		case "ALL":
			removedCnt += removeMatchingBindings(pattern, getBindings);
			removedCnt += removeMatchingBindings(pattern, putBindings);
			removedCnt += removeMatchingBindings(pattern, postBindings);
			removedCnt += removeMatchingBindings(pattern, deleteBindings);
			removedCnt += removeMatchingBindings(pattern, optionsBindings);
			removedCnt += removeMatchingBindings(pattern, headBindings);
			removedCnt += removeMatchingBindings(pattern, traceBindings);
			removedCnt += removeMatchingBindings(pattern, patchBindings);
			removedCnt += removeMatchingBindings(pattern, connectBindings);
			break;
		default:
			throw new RuntimeException(
					"Method did not match any known type was: " + method);
		}
		return removedCnt;
	}
	
	private int removeMatchingBindings(String pattern,
			List<PatternBinding> bindings) {
		int removedCnt = 0;
		for (Iterator<PatternBinding> it = bindings.iterator(); it.hasNext();) {
			PatternBinding b = it.next();
			Matcher m = b.pattern.matcher(pattern);
			if (m.matches()) {
				try {
					lock.writeLock().lock();
					it.remove();
					++removedCnt;
				} finally {
					lock.writeLock().unlock();
				}
			}
		}
		return removedCnt;
	}

  private static void addPattern(String input, Handler<HttpServerRequest> handler, List<PatternBinding> bindings,
			ReentrantReadWriteLock.WriteLock writeLock) {
    // We need to search for any :<token name> tokens in the String and replace them with named capture groups
    Matcher m =  Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)").matcher(input);
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
	try {
		writeLock.lock();
		bindings.add(binding);
	} finally {
		writeLock.unlock();
	}
  }

  private static void addRegEx(String input, Handler<HttpServerRequest> handler, List<PatternBinding> bindings,
			ReentrantReadWriteLock.WriteLock writeLock) {
    PatternBinding binding = new PatternBinding(Pattern.compile(input), null, handler);
	try {
		writeLock.lock();
		bindings.add(binding);
	} finally {
		writeLock.unlock();
	}
  }
  
	/**
	 * If the RouteMatcher is not being updated the route matching does not
	 * incur any overhead other than checking an AtomicBoolean. If the
	 * RouteMatcher is in the updated state, the route matching incurs a
	 * try/finally and {@link ReentrantReadWriteLock.ReadLock} lock/unlock
	 * overhead. Actual RouteMatcher updates momentarily take a
	 * {@link ReentrantReadWriteLock.WriteLock} and very briefly block route
	 * matching.
	 * 
	 * @param request
	 * @param bindings
	 */
	private void route(HttpServerRequest request, List<PatternBinding> bindings) {
		if (updatable.get()) {
			try {
				lock.readLock().lock();
				routeImpl(request, bindings);
			} finally {
				lock.readLock().unlock();
			}
			return;
		}
		routeImpl(request, bindings);
	}

  private void routeImpl(HttpServerRequest request, List<PatternBinding> bindings) {
    for (PatternBinding binding: bindings) {
      Matcher m = binding.pattern.matcher(request.path());
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
        request.params().add(params);
        binding.handler.handle(request);
        return;
      }
    }
    notFound(request);
  }

  private void notFound(HttpServerRequest request) {
    if (noMatchHandler != null) {
      noMatchHandler.handle(request);
    } else {
      // Default 404
      request.response().setStatusCode(404);
      request.response().end();
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