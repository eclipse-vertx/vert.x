/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.dns;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.codegen.annotations.VertxGen;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static io.vertx.core.dns.impl.DnsClientImpl.HEX_TABLE;

/**
 * Provides a way to asynchronously lookup information from DNS servers.
 * <p>
 * Please consult the documentation for more information on DNS clients.
 * <p>
 * The client is thread safe and can be used from any thread.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@VertxGen
public interface DnsClient {

  /**
   * Try to lookup the A (ipv4) or AAAA (ipv6) record for the given name. The first found will be used.
   *
   * @param name  the name to resolve
   * @return a future notified with the resolved address if a record was found. If none was found it
   *                 will get notified with {@code null}. If an error occurs it will get failed.
   */
  Future<@Nullable String> lookup(String name);

  /**
   * Try to lookup the A (ipv4) record for the given name. The first found will be used.
   *
   * @param name  the name to resolve
   * @return a future notified with the resolved {@link java.net.Inet4Address} if a record was found.
   *                 If none was found it will get notified with {@code null}. If an error occurs it will get failed.
   */
  Future<@Nullable String> lookup4(String name);

  /**
   * Try to lookup the AAAA (ipv6) record for the given name. The first found will be used.
   *
   * @param name  the name to resolve
   * @return a future notified  with the resolved {@link java.net.Inet6Address} if a record was found. If none was found
   *                 it will get notified with {@code null}. If an error occurs it will get failed.
   */
  Future<@Nullable String> lookup6(String name);

  /**
   * Try to resolve all A (ipv4) records for the given name.
   *
   * @param name  the name to resolve
   * @return a future notified with a {@link java.util.List} that contains all the resolved
   *                 {@link java.net.Inet4Address}es. If none was found an empty {@link java.util.List} will be used.
   *                 If an error occurs it will get failed.
   */
  Future<List<String>> resolveA(String name);

  /**
   * Try to resolve all AAAA (ipv6) records for the given name.
   *
   * @param name  the name to resolve
   * @return a future notified with a {@link java.util.List} that contains all the resolved
   *                {@link java.net.Inet6Address}es. If none was found an empty {@link java.util.List} will be used.
   *                If an error occurs it will get failed.
   */
  Future<List<String>> resolveAAAA(String name);

  /**
   * Try to resolve the CNAME record for the given name.
   *
   * @param name  the name to resolve the CNAME for
   * @return a future notified with the resolved {@link String} if a record was found. If none was found it will
   *                 get notified with {@code null}. If an error occurs it will get failed.
   */
  Future<List<String>> resolveCNAME(String name);

  /**
   * Try to resolve the MX records for the given name.
   *
   * @param name  the name for which the MX records should be resolved
   * @return a future notified  with a List that contains all resolved {@link MxRecord}s, sorted by
   *                 their {@link MxRecord#priority()}. If none was found it will get notified with an empty
   *                 {@link java.util.List}.  If an error occurs it will get failed.
   */
  Future<List<MxRecord>> resolveMX(String name);

  /**
   * Try to resolve the TXT records for the given name.
   *
   * @param name  the name for which the TXT records should be resolved
   * @return a future notified with a List that contains all resolved {@link String}s. If none was found it will
   *                 get notified with an empty {@link java.util.List}. If an error occurs it will get failed.
   */
  Future<List<String>> resolveTXT(String name);

  /**
   * Try to resolve the PTR record for the given name.
   *
   * @param name  the name to resolve the PTR for
   * @return a future notified with the resolved {@link String} if a record was found. If none was found it will
   *                 get notified with {@code null}. If an error occurs it will get failed.
   */
  Future<@Nullable String> resolvePTR(String name);

  /**
   * Try to resolve the NS records for the given name.
   *
   * @param name  the name for which the NS records should be resolved
   * @return a future notified with a List that contains all resolved {@link String}s. If none was found it will
   *                 get notified with an empty {@link java.util.List}.  If an error occurs it will get failed.
   */
  Future<List<String>> resolveNS(String name);

  /**
   * Try to resolve the SRV records for the given name.
   *
   * @param name  the name for which the SRV records should be resolved
   * @return a future resolved with a List that contains all resolved {@link SrvRecord}s. If none was found it will
   *                 get notified with an empty {@link java.util.List}. If an error occurs it will get failed.
   */
  Future<List<SrvRecord>> resolveSRV(String name);

  /**
   * Try to do a reverse lookup of an IP address. This is basically the same as doing trying to resolve a PTR record
   * but allows you to just pass in the IP address and not a valid ptr query string.
   *
   * @param ipaddress  the IP address to resolve the PTR for
   * @return a future notified with the resolved {@link String} if a record was found. If none was found it will
   *                 get notified with {@code null}. If an error occurs it will get failed.
   */
  default Future<@Nullable String> reverseLookup(String ipaddress) {
    try {
      InetAddress inetAddress = InetAddress.getByName(ipaddress);
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

  /**
   * Close the client.
   *
   * @return the future completed when the client resources have been released
   */
  Future<Void> close();

}
