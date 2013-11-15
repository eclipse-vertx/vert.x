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

package org.vertx.java.core.http.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.ServerWebSocket;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketMatcher implements Handler<ServerWebSocket> {

  private final List<PatternBinding> bindings = new ArrayList<>();

  private Map<String, String> params;
  private Handler<Match> handler;
  private Handler<Match> noMatchHandler;

  @Override
  public void handle(ServerWebSocket ws) {
    String path = ws.path();
    for (PatternBinding binding: bindings) {
      Matcher m = binding.pattern.matcher(path);
      if (m.matches()) {
        params = new HashMap<>(m.groupCount());
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
        handler = binding.handler;
        handler.handle(new Match(params, ws));
        params = null;
        handler = null;
        return;
      }
    }
    if (noMatchHandler != null) {
      noMatchHandler.handle(new Match(null, ws));
    } else {
      ws.reject();
    }
  }

  public void addRegEx(String regex, Handler<Match> handler) {
    PatternBinding binding = new PatternBinding(Pattern.compile(regex), null, handler);
    bindings.add(binding);
  }

  public void addPattern(String pattern, Handler<Match> handler) {
    // We need to search for any :<token name> tokens in the String and replace them with named capture groups
    Matcher m =  Pattern.compile(":([A-Za-z][A-Za-z0-9]*)").matcher(pattern);
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

  /**
   * Specify a handler that will be called when no other handlers match.
   * If this handler is not specified default behaviour is to reject the websocket
   * (i.e. return 404 to the websocket client in the handshake)
   */
  public void noMatch(Handler<Match> handler) {
    noMatchHandler = handler;
  }

  private static class PatternBinding {
    final Pattern pattern;
    final Handler<Match> handler;
    final Set<String> paramNames;

    private PatternBinding(Pattern pattern,  Set<String> paramNames, Handler<Match> handler) {
      this.pattern = pattern;
      this.paramNames = paramNames;
      this.handler = handler;
    }
  }

  public static class Match {
    public final Map<String, String> params;
    public final ServerWebSocket ws;

    public Match(Map<String, String> params, ServerWebSocket ws) {
      this.params = params;
      this.ws = ws;
    }
  }

}
