package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * <p>This class allows you to do route requests based on the HTTP verb and the request URI, in a manner similar
 * to <a href="http://www.sinatrarb.com/">Sinatra</a> or <a href="http://expressjs.com/">Express</a>.
 *
 * <p>RouteMatcher also lets you extract paramaters from the request URI either a simple pattern or using
 * regular expressions for more complex matches. Any parameters extracted will be added to the requests parameters
 * which will be available to you in your request handler.</p>
 *
 * <p>It's particularly useful when writing REST-ful web applications.</p>
 *
 * <p>To use a simple pattern to extract parameters simply prefix the parameter name in the pattern with a ':' (colon).</p>
 *
 * <p>For example:</p>
 *
 * <pre>
 *   RouteMatcher rm = new RouteMatcher();
 *
 *   handler1 = ...
 *
 *   rm.get("/animals/:animal_name/:colour", handler1);
 * </pre>
 *
 * <p>In the above example, if a GET request with a uri of '/animals/dog/black' was received at the server, handler1
 * would be called with request parameter 'animal' set to 'dog', and 'colour' set to 'black'</p>
 *
 * <p>Different handlers can be specified for each of the HTTP verbs, GET, POST, PUT, DELETE etc.</p>
 *
 * <p>For more complex matches regular expressions can be used in the pattern. When regular expressions are used, the extracted
 * parameters do not have a name, so they are put into the HTTP request with names of param0, param1, param2 etc.</p>
 *
 * <p>Multiple matches can be specified for each HTTP verb. In the case there are more than one matching patterns for
 * a particular request, the first matching one will be used.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RouteMatcher implements Handler<HttpServerRequest> {

  private static final Logger log = Logger.getLogger(RouteMatcher.class);

  private List<PatternBinding> getBindings = new ArrayList<>();
  private List<PatternBinding> putBindings = new ArrayList<>();
  private List<PatternBinding> postBindings = new ArrayList<>();
  private List<PatternBinding> deleteBindings = new ArrayList<>();
  private List<PatternBinding> optionsBindings = new ArrayList<>();
  private List<PatternBinding> headBindings = new ArrayList<>();
  private List<PatternBinding> traceBindings = new ArrayList<>();
  private List<PatternBinding> connectBindings = new ArrayList<>();
  private List<PatternBinding> patchBindings = new ArrayList<>();

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
  public void get(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, getBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP PUT
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public void put(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, putBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP POST
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public void post(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, postBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP DELETE
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public void delete(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, deleteBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP OPTIONS
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public void options(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, optionsBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP HEAD
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public void head(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, headBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP TRACE
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public void trace(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, traceBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP CONNECT
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public void connect(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, connectBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP PATCH
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public void patch(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, patchBindings);
  }

  /**
   * Specify a handler that will be called for all HTTP methods
   * @param pattern The simple pattern
   * @param handler The handler to call
   */
  public void all(String pattern, Handler<HttpServerRequest> handler) {
    addPattern(pattern, handler, getBindings);
    addPattern(pattern, handler, putBindings);
    addPattern(pattern, handler, postBindings);
    addPattern(pattern, handler, deleteBindings);
    addPattern(pattern, handler, optionsBindings);
    addPattern(pattern, handler, headBindings);
    addPattern(pattern, handler, traceBindings);
    addPattern(pattern, handler, connectBindings);
    addPattern(pattern, handler, patchBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP GET
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void getWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, getBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP PUT
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void putWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, putBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP POST
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void postWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, postBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP DELETE
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void deleteWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, deleteBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP OPTIONS
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void optionsWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, optionsBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP HEAD
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void headWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, headBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP TRACE
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void traceWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, traceBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP CONNECT
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void connectWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, connectBindings);
  }

  /**
   * Specify a handler that will be called for a matching HTTP PATCH
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void patchWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, patchBindings);
  }

  /**
   * Specify a handler that will be called for all HTTP methods
   * @param regex A regular expression
   * @param handler The handler to call
   */
  public void allWithRegEx(String regex, Handler<HttpServerRequest> handler) {
    addRegEx(regex, handler, getBindings);
    addRegEx(regex, handler, putBindings);
    addRegEx(regex, handler, postBindings);
    addRegEx(regex, handler, deleteBindings);
    addRegEx(regex, handler, optionsBindings);
    addRegEx(regex, handler, headBindings);
    addRegEx(regex, handler, traceBindings);
    addRegEx(regex, handler, connectBindings);
    addRegEx(regex, handler, patchBindings);
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
        request.getAllParams().putAll(params);
        binding.handler.handle(request);
        return;
      }
    }
    // If we get here it wasn't routed
    request.response.statusCode = 404;
    request.response.end();
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
