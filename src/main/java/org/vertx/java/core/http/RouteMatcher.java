package org.vertx.java.core.http;

import org.vertx.java.core.Handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RouteMatcher implements Handler<HttpServerRequest> {

  private List<PatternBinding> getBindings = new CopyOnWriteArrayList<>();
  private List<PatternBinding> putBindings = new CopyOnWriteArrayList<>();
  private List<PatternBinding> postBindings = new CopyOnWriteArrayList<>();
  private List<PatternBinding> deleteBindings = new CopyOnWriteArrayList<>();

  @Override
  public void handle(HttpServerRequest request) {
    switch (request.method) {
      case "GET":
        System.out.println("Routing a GET");
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
    }
  }

  public void get(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, getBindings);
  }

  public void put(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, putBindings);
  }

  public void post(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, postBindings);
  }

  public void delete(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, deleteBindings);
  }

  public void getWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, getBindings);
  }

  public void putWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, putBindings);
  }

  public void postWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, postBindings);
  }

  public void deleteWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, deleteBindings);
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
        request.getParams().putAll(params);
        System.out.println("Routing it");
        binding.handler.handle(request);
        return;
      }
    }
    // If we get here it wasn't routed
    request.response.statusCode = 404;
    request.response.end();
    System.out.println("Not routed");
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
