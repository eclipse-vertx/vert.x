package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSocketMatcher implements WebSocketHandler {

  private static final Logger log = Logger.getLogger(RouteMatcher.class);

  private List<PatternBinding> bindings = new CopyOnWriteArrayList<>();

  private Map<String, String> params;
  private Handler<Match> handler;

  @Override
  public void handle(WebSocket ws) {
    handler.handle(new Match(params, ws));
    params = null;
    handler = null;
  }

  @Override
  public boolean accept(String path) {
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
        return true;
      }
    }
    return false;
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
    public final WebSocket ws;

    public Match(Map<String, String> params, WebSocket ws) {
      this.params = params;
      this.ws = ws;
    }
  }

}
