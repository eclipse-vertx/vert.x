/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.impl.UriParser;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Alt-svc parsed value.
 */
public interface AltSvc {

  /**
   * Alt-Svc: Clear.
   */
  class Clear implements AltSvc {
    public static final Clear INSTANCE = new Clear();
    private Clear() {
    }
  }

  class ListOfValues extends ArrayList<Value> implements AltSvc {
    public ListOfValues() {
      super(3);
    }
  }

  /**
   * Alt-Svc: Value.
   */
  public static class Value {
    private String protocolId;
    private String rawAltAuthority;
    private HostAndPort altAuthority;
    private Map<String, String> parameters = Map.of();
    private Value() {
    }

    /**
     * @return the protocol id
     */
    public String protocolId() {
      return protocolId;
    }

    /**
     * @return the alt authority
     */
    public HostAndPort altAuthority() {
      return altAuthority;
    }

    /**
     * @return the parameter map
     */
    public Map<String, String> parameters() {
      return parameters;
    }

    private void setAlternative(String protocolId, String altAuthority) {
      this.protocolId = protocolId;
      this.rawAltAuthority = altAuthority;
    }

    private void addParameter(String name, String value) {
      if (parameters.isEmpty()) {
        parameters = new HashMap<>(2);
      }
      parameters.put(name, value);
    }
  }

  public static AltSvc parseAltSvc(String s) {
    if (s.equals("clear")) {
      return Clear.INSTANCE;
    }
    int from = 0;
    int to = s.length();
    ListOfValues values = new ListOfValues();
    while (true) {
      Value value = new Value();
      from = parseAltValue(s, from, to, value);
      if (from == -1) {
        return null;
      }
      values.add(value);
      if (from == to) {
        break;
      }
      from = HttpParser.parseOWS(s, from, to);
      if (from >= to || s.charAt(from++) != ',') {
        return null;
      }
      from = HttpParser.parseOWS(s, from, to);
    }

    return values.isEmpty() ? null : values;
  }

  public static Value parseAltValue(String s) {
    Value value = new Value();
    int res = parseAltValue(s, 0, s.length(), value);
    return res == s.length() ? value : null;
  }

  public static int parseAltValue(String s, int from, int to, Value value) {
    int res = parseAltValue(s, from, to, value::setAlternative, value::addParameter);
    if (res == s.length()) {
      // Now parse raw alt-authority into an HostAndPort
      String raw = value.rawAltAuthority;
      String host;
      int idx;
      if (raw.charAt(0) == ':') {
        idx = 0;
        host = "";
      } else {
        idx = UriParser.parseHost(raw, 0, raw.length());
        if (idx == -1 || idx >= raw.length() || raw.charAt(idx) != ':') {
          return -1;
        }
        host = raw.substring(0, idx);
      }
      int port = UriParser.parseAndEvalPort(raw, idx);
      if (port != -1) {
        value.altAuthority = HostAndPort.create(host, port);
      }
    }
    return res;
  }

  public static int parseAltValue(String s, int from, int to) {
    return parseAltValue(s, from, to, null, null);
  }

  public static int parseAltValue(String s, int from, int to, BiConsumer<String, String> alternative, BiConsumer<String, String> parameter) {
    from = parseAlternative(s, from, to, alternative);
    if (from == -1) {
      return -1;
    }
    while (true) {
      int idx = HttpParser.parseOWS(s, from, to);
      if (idx == -1 || idx >= to || s.charAt(idx++) != ';') {
        return from;
      }
      idx = HttpParser.parseOWS(s, idx, to);
      if (idx == -1) {
        return from;
      }
      idx = parseParameter(s, idx, to, parameter);
      if (idx == -1) {
        return from;
      }
      from = idx;
    }
  }

  public static int parseParameter(String s, int from, int to) {
    return parseParameter(s, from, to, null);
  }

  public static int parseParameter(String s, int from, int to, BiConsumer<String, String> parameter) {
    if (from >= to) {
      return -1;
    }
    int endOfName = HttpParser.parseToken(s, from, to);
    if (endOfName == -1 || endOfName >= to || s.charAt(endOfName) != '=') {
      return -1;
    }
    int endOfValue = HttpParser.parseToken(s, endOfName + 1, to);
    if (endOfValue == -1) {
      endOfValue = HttpParser.parseQuotedString(s, endOfName + 1, to);
    }
    if (endOfValue == -1) {
      return -1;
    }
    if (parameter != null) {
      String name = s.substring(from, endOfName);
      String value;
      if (s.charAt(endOfName + 1) == '\"') {
        value = s.substring(endOfName + 2, endOfValue - 1);
      } else {
        value = s.substring(endOfName + 1, endOfValue);
      }
      parameter.accept(name, value);
    }
    return endOfValue;
  }

  public static int parseAlternative(String s, int from, int to) {
    return parseAlternative(s, from, to, null);
  }

  public static int parseAlternative(String s, int from, int to, BiConsumer<String, String> alternative) {
    int endOfProtocolId = parseProtocolId(s, from, to);
    if (endOfProtocolId == -1 || endOfProtocolId >= to || s.charAt(endOfProtocolId) != '=') {
      return -1;
    }
    int endOfAltAuthority = parseAltAuthority(s, endOfProtocolId + 1, to);
    if (endOfAltAuthority == -1) {
      return -1;
    }
    if (alternative != null) {
      String protocolId = s.substring(from, endOfProtocolId);
      String altAuthority;
      if (s.charAt(endOfProtocolId + 1) == '\"') {
        altAuthority = s.substring(endOfProtocolId + 2, endOfAltAuthority - 1);
      } else {
        altAuthority = s.substring(endOfProtocolId + 1, endOfAltAuthority);
      }
      alternative.accept(protocolId, altAuthority);
    }
    return endOfAltAuthority;
  }

  public static int parseAltAuthority(String s, int from, int to) {
    return HttpParser.parseQuotedString(s, from, to);
  }

  public static int parseProtocolId(String s, int from, int to) {
    return HttpParser.parseToken(s, from, to);
  }
}
