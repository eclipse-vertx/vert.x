/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.dns;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration options for Vert.x hostname resolver.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class HostnameResolverOptions {

  /**
   * The default list of DNS servers = null (uses system name server's list like resolve.conf otherwise Google Public DNS)
   */
  public static final List<String> DEFAULT_DNS_SERVERS = null;

  /**
   * The default value for {@link #setOptResourceEnabled} = true
   */
  public static final boolean DEFAULT_OPT_RESOURCE_ENABLED = true;

  private List<String> servers = DEFAULT_DNS_SERVERS;
  private boolean optResourceEnabled = DEFAULT_OPT_RESOURCE_ENABLED;

  public HostnameResolverOptions() {
  }

  public HostnameResolverOptions(HostnameResolverOptions other) {
    this.servers = other.servers != null ? new ArrayList<>(other.servers) : null;
    this.optResourceEnabled = other.optResourceEnabled;
  }

  public HostnameResolverOptions(JsonObject json) {
    HostnameResolverOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the list of dns server
   */
  public List<String> getServers() {
    return servers;
  }

  /**
   * Set the list of DNS server addresses, an address is the IP  of the dns server, followed by an optional
   * colon and a port, e.g {@code 8.8.8.8} or {code 192.168.0.1:40000}. When the list is empty, the resolver
   * will use the list of the system DNS server addresses from the environment, if that list cannot be retrieved
   * it will use Google's public DNS servers {@code "8.8.8.8"} and {@code "8.8.4.4"}.
   *
   * @param servers the list of DNS servers
   * @return a reference to this, so the API can be used fluently
   */
  public HostnameResolverOptions setServers(List<String> servers) {
    this.servers = servers;
    return this;
  }

  /**
   * Add a DNS server address.
   *
   * @param server the server to add
   * @return a reference to this, so the API can be used fluently
   */
  public HostnameResolverOptions addServer(String server) {
    if (servers == null) {
      servers = new ArrayList<>();
    }
    servers.add(server);
    return this;
  }

  /**
   * @return true if an optional record is automatically included in DNS queries
   */
  public boolean isOptResourceEnabled() {
    return optResourceEnabled;
  }

  /**
   * Set to true to enable the automatic inclusion in DNS queries of an optional record that hints
   * the remote DNS server about how much data the resolver can read per response.
   *
   * @param optResourceEnabled true to enable, false otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public HostnameResolverOptions setOptResourceEnabled(boolean optResourceEnabled) {
    this.optResourceEnabled = optResourceEnabled;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HostnameResolverOptions that = (HostnameResolverOptions) o;
    if (optResourceEnabled != that.optResourceEnabled) return false;
    return servers != null ? servers.equals(that.servers) : that.servers == null;
  }

  @Override
  public int hashCode() {
    int result = optResourceEnabled ? 1 : 0;
    result = 31 * result + (servers != null ? servers.hashCode() : 0);
    return result;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    HostnameResolverOptionsConverter.toJson(this, json);
    return json;
  }
}
