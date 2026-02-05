package io.vertx.test.fakemetrics;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.spi.metrics.WebSocketMetrics;
import io.vertx.core.spi.observability.HttpRequest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FakeWebSocketMetrics extends FakeMetricsBase implements WebSocketMetrics<WebSocketMetric> {

  private final ConcurrentMap<String, WebSocketMetric> webSockets = new ConcurrentHashMap<>();

  public WebSocketMetric webSocketMetric(String uri) {
    return webSockets.get(uri);
  }

  public WebSocketMetric webSocketMetric(ServerWebSocket ws) {
    return webSocketMetric(ws.path());
  }

  @Override
  public WebSocketMetric connected(HttpRequest request) {
    WebSocketMetric metric = new WebSocketMetric(request);
    webSockets.put(request.uri(), metric);
    return metric;
  }

  @Override
  public void disconnected(WebSocketMetric webSocketMetric) {
    webSockets.remove(webSocketMetric.request.uri());
  }
}
