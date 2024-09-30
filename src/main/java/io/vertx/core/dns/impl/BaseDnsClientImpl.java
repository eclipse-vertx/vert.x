package io.vertx.core.dns.impl;

import io.netty.handler.codec.dns.DnsRecordType;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.dns.MxRecord;
import io.vertx.core.dns.SrvRecord;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 *
 * Base class for both DnsClient and DohClient, containing some common methods shared between them.
 *
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */


public abstract class BaseDnsClientImpl implements DnsClient {
  private static final char[] HEX_TABLE = "0123456789abcdef".toCharArray();
  protected DnsClientOptions options;
  protected VertxInternal vertx;
  protected ContextInternal context;
  protected volatile Future<Void> closed;

  protected abstract  <T> Future<List<T>> lookupList(String name, DnsRecordType... types);

  @Override
  public DnsClient lookup4(String name, Handler<AsyncResult<String>> handler) {
    lookup4(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> lookup4(String name) {
    return lookupSingle(name, DnsRecordType.A);
  }

  @Override
  public DnsClient lookup6(String name, Handler<AsyncResult<String>> handler) {
    lookup6(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> lookup6(String name) {
    return lookupSingle(name, DnsRecordType.AAAA);
  }

  @Override
  public DnsClient lookup(String name, Handler<AsyncResult<String>> handler) {
    lookup(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> lookup(String name) {
    return lookupSingle(name, DnsRecordType.A, DnsRecordType.AAAA);
  }

  @Override
  public DnsClient resolveA(String name, Handler<AsyncResult<List<String>>> handler) {
    resolveA(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<String>> resolveA(String name) {
    return lookupList(name, DnsRecordType.A);
  }

  @Override
  public DnsClient resolveCNAME(String name, Handler<AsyncResult<List<String> >> handler) {
    resolveCNAME(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<String>> resolveCNAME(String name) {
    return lookupList(name, DnsRecordType.CNAME);
  }

  @Override
  public DnsClient resolveMX(String name, Handler<AsyncResult<List<MxRecord>>> handler) {
    resolveMX(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<MxRecord>> resolveMX(String name) {
    return lookupList(name, DnsRecordType.MX);
  }

  @Override
  public DnsClient resolveTXT(String name, Handler<AsyncResult<List<String>>> handler) {
    resolveTXT(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> resolvePTR(String name) {
    return lookupSingle(name, DnsRecordType.PTR);
  }

  @Override
  public DnsClient resolvePTR(String name, Handler<AsyncResult<String>> handler) {
    resolvePTR(name).onComplete(handler);
    return this;
  }

  @Override
  public DnsClient resolveAAAA(String name, Handler<AsyncResult<List<String>>> handler) {
    resolveAAAA(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<String>> resolveAAAA(String name) {
    return lookupList(name, DnsRecordType.AAAA);
  }

  @Override
  public Future<List<String>> resolveNS(String name) {
    return lookupList(name, DnsRecordType.NS);
  }

  @Override
  public DnsClient resolveNS(String name, Handler<AsyncResult<List<String>>> handler) {
    resolveNS(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<List<SrvRecord>> resolveSRV(String name) {
    return lookupList(name, DnsRecordType.SRV);
  }

  @Override
  public DnsClient resolveSRV(String name, Handler<AsyncResult<List<SrvRecord>>> handler) {
    resolveSRV(name).onComplete(handler);
    return this;
  }

  @Override
  public Future<@Nullable String> reverseLookup(String address) {
    try {
      InetAddress inetAddress = InetAddress.getByName(address);
      byte[] addr = inetAddress.getAddress();

      StringBuilder reverseName = new StringBuilder(64);
      if (inetAddress instanceof Inet4Address) {
        // reverse ipv4 address
        reverseName.append(addr[3] & 0xff).append(".")
          .append(addr[2]& 0xff).append(".")
          .append(addr[1]& 0xff).append(".")
          .append(addr[0]& 0xff);
      } else {
        // It is an ipv 6 address time to reverse it
        for (int i = 0; i < 16; i++) {
          reverseName.append(HEX_TABLE[(addr[15 - i] & 0xf)]);
          reverseName.append(".");
          reverseName.append(HEX_TABLE[(addr[15 - i] >> 4) & 0xf]);
          if (i != 15) {
            reverseName.append(".");
          }
        }
      }
      reverseName.append(".in-addr.arpa");

      return resolvePTR(reverseName.toString());
    } catch (UnknownHostException e) {
      // Should never happen as we work with ip addresses as input
      // anyway just in case notify the handler
      return Future.failedFuture(e);
    }
  }

  @Override
  public DnsClient reverseLookup(String address, Handler<AsyncResult<String>> handler) {
    reverseLookup(address).onComplete(handler);
    return this;
  }

  private <T> Future<T> lookupSingle(String name, DnsRecordType... types) {
    return this.<T>lookupList(name, types).map(result -> result.isEmpty() ? null : result.get(0));
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    close().onComplete(handler);
  }
}
