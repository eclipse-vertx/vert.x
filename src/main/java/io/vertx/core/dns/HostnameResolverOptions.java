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
 * Configuration options for Vert.x hostname resolver. The resolver uses the local <i>hosts</i> file and performs
 * DNS <i>A</i> and <i>AAAA</i> queries.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject(generateConverter = true)
public class HostnameResolverOptions {

  /**
   * The default list of DNS servers = null (uses system name server's list like resolve.conf otherwise Google Public DNS)
   */
  public static final List<String> DEFAULT_SERVERS = null;

  /**
   * The default value for {@link #setOptResourceEnabled} = true
   */
  public static final boolean DEFAULT_OPT_RESOURCE_ENABLED = true;

  public static final int DEFAULT_CACHE_MIN_TIME_TO_LIVE = 0;
  public static final int DEFAULT_CACHE_MAX_TIME_TO_LIVE = Integer.MAX_VALUE;
  public static final int DEFAULT_CACHE_NEGATIVE_TIME_TO_LIVE = 0;
  public static final int DEFAULT_QUERY_TIMEOUT = 5000;
  public static final int DEFAULT_MAX_QUERIES = 3;
  public static final boolean DEFAULT_RD_FLAG = true;

  private List<String> servers;
  private boolean optResourceEnabled;
  private int cacheMinTimeToLive;
  private int cacheMaxTimeToLive;
  private int cacheNegativeTimeToLive;
  private long queryTimeout;
  private int maxQueries;
  private boolean rdFlag;

  public HostnameResolverOptions() {
    servers = DEFAULT_SERVERS;
    optResourceEnabled = DEFAULT_OPT_RESOURCE_ENABLED;
    cacheMinTimeToLive = DEFAULT_CACHE_MIN_TIME_TO_LIVE;
    cacheMaxTimeToLive = DEFAULT_CACHE_MAX_TIME_TO_LIVE;
    cacheNegativeTimeToLive = DEFAULT_CACHE_NEGATIVE_TIME_TO_LIVE;
    queryTimeout = DEFAULT_QUERY_TIMEOUT;
    maxQueries = DEFAULT_MAX_QUERIES;
    rdFlag = DEFAULT_RD_FLAG;
  }

  public HostnameResolverOptions(HostnameResolverOptions other) {
    this.servers = other.servers != null ? new ArrayList<>(other.servers) : null;
    this.optResourceEnabled = other.optResourceEnabled;
    this.cacheMinTimeToLive = other.cacheMinTimeToLive;
    this.cacheMaxTimeToLive = other.cacheMaxTimeToLive;
    this.cacheNegativeTimeToLive = other.cacheNegativeTimeToLive;
    this.queryTimeout = other.queryTimeout;
    this.maxQueries = other.maxQueries;
    this.rdFlag = other.rdFlag;
  }

  public HostnameResolverOptions(JsonObject json) {
    this();
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

  /**
   * @return the cache min TTL in seconds
   */
  public int getCacheMinTimeToLive() {
    return cacheMinTimeToLive;
  }

  /**
   * Set the cache minimum TTL value in seconds. After resolution successful IP addresses are cached with their DNS response TTL,
   * use this to set a minimum value to all responses TTL.
   *
   * @param cacheMinTimeToLive the cache min TTL in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public HostnameResolverOptions setCacheMinTimeToLive(int cacheMinTimeToLive) {
    this.cacheMinTimeToLive = cacheMinTimeToLive;
    return this;
  }

  /**
   * @return the cache max TTL in seconds
   */
  public int getCacheMaxTimeToLive() {
    return cacheMaxTimeToLive;
  }

  /**
   * Set the cache maximum TTL value in seconds. After successful resolution IP addresses are cached with their DNS response TTL,
   * use this to set a maximum value to all responses TTL.
   *
   * @param cacheMaxTimeToLive the cache max TTL in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public HostnameResolverOptions setCacheMaxTimeToLive(int cacheMaxTimeToLive) {
    this.cacheMaxTimeToLive = cacheMaxTimeToLive;
    return this;
  }

  /**
   * @return the cache negative TTL in seconds
   */
  public int getCacheNegativeTimeToLive() {
    return cacheNegativeTimeToLive;
  }

  /**
   * Set the negative cache TTL value in seconds. After a failed hostname resolution, DNS queries won't be retried
   * for a period of time equals to the negative TTL. This allows to reduce the response time of negative replies
   * and reduce the amount of messages to DNS servers.
   *
   * @param cacheNegativeTimeToLive the cache negative TTL in seconds
   * @return a reference to this, so the API can be used fluently
   */
  public HostnameResolverOptions setCacheNegativeTimeToLive(int cacheNegativeTimeToLive) {
    this.cacheNegativeTimeToLive = cacheNegativeTimeToLive;
    return this;
  }

  /**
   * @return the query timeout in milliseconds
   */
  public long getQueryTimeout() {
    return queryTimeout;
  }

  /**
   * Set the query timeout in milliseconds, i.e the amount of time after a query is considered to be failed.
   *
   * @param queryTimeout the query timeout in milliseconds
   * @return a reference to this, so the API can be used fluently
   */
  public HostnameResolverOptions setQueryTimeout(long queryTimeout) {
    this.queryTimeout = queryTimeout;
    return this;
  }

  /**
   * @return the maximum number of queries to be sent during a resolution
   */
  public int getMaxQueries() {
    return maxQueries;
  }

  /**
   * Set the maximum number of queries when an hostname is resolved.
   *
   * @param maxQueries the max number of queries to be sent
   * @return a reference to this, so the API can be used fluently
   */
  public HostnameResolverOptions setMaxQueries(int maxQueries) {
    this.maxQueries = maxQueries;
    return this;
  }

  /**
   * @return the DNS queries <i>Recursion Desired</i> flag value
   */
  public boolean getRdFlag() {
    return rdFlag;
  }

  /**
   * Set the DNS queries <i>Recursion Desired</i> flag value.
   *
   * @param rdFlag the flag value
   * @return a reference to this, so the API can be used fluently
   */
  public HostnameResolverOptions setRdFlag(boolean rdFlag) {
    this.rdFlag = rdFlag;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HostnameResolverOptions that = (HostnameResolverOptions) o;
    if (optResourceEnabled != that.optResourceEnabled) return false;
    if (cacheMinTimeToLive != that.cacheMinTimeToLive) return false;
    if (cacheMaxTimeToLive != that.cacheMaxTimeToLive) return false;
    if (cacheNegativeTimeToLive != that.cacheNegativeTimeToLive) return false;
    if (queryTimeout != that.queryTimeout) return false;
    if (maxQueries != that.maxQueries) return false;
    if (rdFlag != that.rdFlag) return false;
    return servers != null ? servers.equals(that.servers) : that.servers == null;
  }

  @Override
  public int hashCode() {
    int result = optResourceEnabled ? 1 : 0;
    result = 31 * result + (servers != null ? servers.hashCode() : 0);
    result = 31 * result + cacheMinTimeToLive;
    result = 31 * result + cacheMaxTimeToLive;
    result = 31 * result + cacheNegativeTimeToLive;
    result = 31 * result + Long.hashCode(queryTimeout);
    result = 31 * result + maxQueries;
    result = 31 * result + Boolean.hashCode(rdFlag);
    return result;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    HostnameResolverOptionsConverter.toJson(this, json);
    return json;
  }
}
