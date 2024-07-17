package io.vertx.core.dns.impl;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.dns.DnsRecordType;
import io.vertx.core.*;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.dns.DnsException;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.dns.dnsrecord.DohRecord;
import io.vertx.core.dns.dnsrecord.DohResourceRecord;
import io.vertx.core.dns.impl.decoder.DohRecordDecoder;
import io.vertx.core.dns.impl.decoder.JsonHelper;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static io.vertx.core.dns.impl.decoder.JsonHelper.appendDotIfRequired;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */

public class DohClientImpl extends BaseDnsClientImpl {
  public static final String DOH_URL_FORMAT = "/dns-query?name=%s&type=%s";
  private final HttpClient httpClient;
  private final ConcurrentMap<Integer, Query<?>> inProgressMap = new ConcurrentHashMap<>();

  public DohClientImpl(VertxInternal vertx, DnsClientOptions options) {
    Objects.requireNonNull(options, "no null options accepted");
    Objects.requireNonNull(options.getHost(), "no null host accepted");

    this.options = new DnsClientOptions(options);

    this.vertx = vertx;
    this.context = vertx.getOrCreateContext();

    HttpClientOptions httpClientOptions = new HttpClientOptions()
      .setLogActivity(options.getLogActivity())
      .setActivityLogDataFormat(options.getActivityLogFormat())
      .setSsl(true)
      .setUseAlpn(true)
      .setTrustAll(true)
      .setVerifyHost(false)
      .setProtocolVersion(HttpVersion.HTTP_2);

    this.httpClient = vertx.createHttpClient(httpClientOptions);
  }

  protected <T> Future<List<T>> lookupList(String name, DnsRecordType... types) {
    ContextInternal ctx = vertx.getOrCreateContext();
    if (closed != null) {
      return ctx.failedFuture(ConnectionBase.CLOSED_EXCEPTION);
    }
    Objects.requireNonNull(name, "no null name accepted");

    Query<T> query = new Query<>(name, types);
    return query.run();
  }

  private class Query<T> {
    final Promise<List<T>> promise;
    final String name;
    final DnsRecordType[] types;
    long timerID;
    int id;

    public Query(String name, DnsRecordType[] types) {
      this.types = types;
      this.promise = Promise.promise();
      this.name = appendDotIfRequired(name);
      this.id = ThreadLocalRandom.current().nextInt();
    }

    public Future<List<T>> run() {
      inProgressMap.put(id, this);
      timerID = vertx.setTimer(options.getQueryTimeout(), id -> {
        timerID = -1;
        context.runOnContext(v -> fail(new VertxException("DNS query timeout for " + name)));
      });

      List<Future<List<T>>> futures = Arrays.stream(types).map(this::fetchDns).collect(Collectors.toList());

      Future.all(futures)
        .onSuccess(compositeFuture -> promise.tryComplete(combineCompositeFutureResults(compositeFuture)))
        .onFailure(t -> context.emit(t, this::fail));

      return promise.future();
    }

    private Future<List<T>> fetchDns(DnsRecordType type) {
      RequestOptions requestOptions = new RequestOptions()
        .setSsl(true)
        .setPort(options.getPort())
        .setHost(options.getHost())
        .setURI(String.format(DOH_URL_FORMAT, name, type.name()))
        .setMethod(HttpMethod.GET)
        .addHeader("accept", "application/dns-json");

      return httpClient.request(requestOptions)
        .compose(HttpClientRequest::send)
        .compose(HttpClientResponse::body)
        .map(buf -> new DohRecord(JsonHelper.normalizePropertyNames(new JsonObject(buf))))
        .compose(this::handleResolvedDns);
    }

    private List<T> combineCompositeFutureResults(CompositeFuture compositeFuture) {
      List<T> result = new ArrayList<>();
      for (int i = 0; i < compositeFuture.size(); i++) {
        if (compositeFuture.resultAt(i) instanceof List) {
          result.addAll(compositeFuture.<List<T>>resultAt(i));
        } else {
          result.add(compositeFuture.resultAt(i));
        }
      }
      return result;
    }

    private Future<List<T>> handleResolvedDns(DohRecord resolvedDns) {
      DnsResponseCode code = DnsResponseCode.valueOf(resolvedDns.getStatus());

      inProgressMap.remove(id);
      if (timerID >= 0) {
        vertx.cancelTimer(timerID);
      }

      if (code != DnsResponseCode.NOERROR) {
        fail(new DnsException(code));
        return promise.future();
      }

      List<T> result = new ArrayList<>();

      if (resolvedDns.getAnswer() != null) {
        for (DohResourceRecord answer : resolvedDns.getAnswer()) {
          T decoded = decodeResourceRecord(answer);
          if (decoded == null) {
            return promise.future();
          }
          if (isRequestedType(DnsRecordType.valueOf(answer.getType()), types)) {
            result.add(decoded);
          }
        }
      }
      if (resolvedDns.getAuthority() != null) {
        for (DohResourceRecord authority : resolvedDns.getAuthority()) {
          result.add(DohRecordDecoder.decode(authority));
        }
      }

      return Future.succeededFuture(result);
    }

    private T decodeResourceRecord(DohResourceRecord resourceRecord) {
      try {
        return DohRecordDecoder.decode(resourceRecord);
      } catch (DecoderException e) {
        fail(e);
      }
      return null;
    }

    private void fail(Throwable cause) {
      inProgressMap.remove(id);
      if (timerID >= 0) {
        vertx.cancelTimer(timerID);
      }
      promise.tryFail(cause);
    }

    private boolean isRequestedType(DnsRecordType dnsRecordType, DnsRecordType[] types) {
      List<DnsRecordType> dnsRecordTypes = Arrays.asList(types);
//      if(dnsRecordTypes.contains(DnsRecordType.SRV) && dnsRecordType == DnsRecordType.SOA) {
//        return true;
//      }
      return dnsRecordTypes.contains(dnsRecordType);
    }
  }

  public void inProgressQueries(Handler<Integer> handler) {
    context.runOnContext(v -> handler.handle(inProgressMap.size()));
  }

  @Override
  public Future<List<String>> resolveTXT(String name) {
    return this.lookupList(name, DnsRecordType.TXT);
  }

  @Override
  public Future<Void> close() {
    synchronized (this) {
      if (closed != null) {
        return closed;
      }
      closed = Future.succeededFuture();
    }
    inProgressMap.values().forEach(query -> query.fail(ConnectionBase.CLOSED_EXCEPTION));
    return closed;
  }
}
