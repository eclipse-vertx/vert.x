/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.dns;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.resolver.NameResolver;
import io.vertx.core.json.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static io.vertx.core.impl.Utils.isLinux;
import static io.vertx.core.internal.resolver.NameResolver.parseLinux;

/**
 * Configuration options for Vert.x hostname resolver. The resolver uses the local <i>hosts</i> file and performs
 * DNS <i>A</i> and <i>AAAA</i> queries.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class AddressResolverOptions {

  static {
    if (isLinux()) {
      NameResolver.ResolverOptions options = parseLinux(new File("/etc/resolv.conf"));
      DEFAULT_NDOTS = options.effectiveNdots();
      DEFAULT_ROTATE_SERVERS = options.isRotate();
    } else {
      DEFAULT_NDOTS = 1;
      DEFAULT_ROTATE_SERVERS = false;
    }
  }

  /**
   * The default list of DNS servers = null (uses system name server's list like resolve.conf otherwise Google Public DNS)
   */
  public static final List<String> DEFAULT_SERVERS = null;

  /**
   * The default value for {@link #setOptResourceEnabled} = false
   */
  public static final boolean DEFAULT_OPT_RESOURCE_ENABLED = false;

  /**
   * The default value for the cache min TTL = 0
   */
  public static final int DEFAULT_CACHE_MIN_TIME_TO_LIVE = 0;

  /**
   * The default value for the cache max TTL = 0x7fffffff
   */
  public static final int DEFAULT_CACHE_MAX_TIME_TO_LIVE = Integer.MAX_VALUE;

  /**
   * The default value for the negative cache TTL = 0
   */
  public static final int DEFAULT_CACHE_NEGATIVE_TIME_TO_LIVE = 0;

  /**
   * The default value for the query timeout in millis = 5000
   */
  public static final int DEFAULT_QUERY_TIMEOUT = 5000;

  /**
   * The default value for the hosts refresh value in millis = 0 (disabled)
   */
  public static final int DEFAULT_HOSTS_REFRESH_PERIOD = 0;

  /**
   * The default value for the max dns queries per query = 4
   */
  public static final int DEFAULT_MAX_QUERIES = 4;

  /**
   * The default value of the rd flag = true
   */
  public static final boolean DEFAULT_RD_FLAG = true;

  /**
   * The default value of search domains = null
   */
  public static final List<String> DEFAULT_SEARCH_DOMAINS = null;

  /**
   * The default ndots value = loads the value from the OS on Linux otherwise use the value 1
   */
  public static final int DEFAULT_NDOTS;

  /**
   * The default servers rotate value = loads the value from the OS on Linux otherwise use the value false
   */
  public static final boolean DEFAULT_ROTATE_SERVERS;

  /**
   * The default round-robin inet address = false
   */
  public static final boolean DEFAULT_ROUND_ROBIN_INET_ADDRESS = false;

  private String hostsPath;
  private Buffer hostsValue;
  private int hostsRefreshPeriod;
  private List<String> servers;
  private boolean optResourceEnabled;
  private int cacheMinTimeToLive;
  private int cacheMaxTimeToLive;
  private int cacheNegativeTimeToLive;
  private long queryTimeout;
  private int maxQueries;
  private boolean rdFlag;
  private List<String> searchDomains;
  private int ndots;
  private boolean rotateServers;
  private boolean roundRobinInetAddress;

  public AddressResolverOptions() {
    servers = DEFAULT_SERVERS;
    optResourceEnabled = DEFAULT_OPT_RESOURCE_ENABLED;
    cacheMinTimeToLive = DEFAULT_CACHE_MIN_TIME_TO_LIVE;
    cacheMaxTimeToLive = DEFAULT_CACHE_MAX_TIME_TO_LIVE;
    cacheNegativeTimeToLive = DEFAULT_CACHE_NEGATIVE_TIME_TO_LIVE;
    queryTimeout = DEFAULT_QUERY_TIMEOUT;
    maxQueries = DEFAULT_MAX_QUERIES;
    rdFlag = DEFAULT_RD_FLAG;
    searchDomains = DEFAULT_SEARCH_DOMAINS;
    ndots = DEFAULT_NDOTS;
    rotateServers = DEFAULT_ROTATE_SERVERS;
    roundRobinInetAddress = DEFAULT_ROUND_ROBIN_INET_ADDRESS;
    hostsRefreshPeriod = DEFAULT_HOSTS_REFRESH_PERIOD;
  }

  public AddressResolverOptions(AddressResolverOptions other) {
    this.hostsPath = other.hostsPath;
    this.hostsValue = other.hostsValue != null ? other.hostsValue.copy() : null;
    this.hostsRefreshPeriod = other.hostsRefreshPeriod;
    this.servers = other.servers != null ? new ArrayList<>(other.servers) : null;
    this.optResourceEnabled = other.optResourceEnabled;
    this.cacheMinTimeToLive = other.cacheMinTimeToLive;
    this.cacheMaxTimeToLive = other.cacheMaxTimeToLive;
    this.cacheNegativeTimeToLive = other.cacheNegativeTimeToLive;
    this.queryTimeout = other.queryTimeout;
    this.maxQueries = other.maxQueries;
    this.rdFlag = other.rdFlag;
    this.searchDomains = other.searchDomains != null ? new ArrayList<>(other.searchDomains) : null;
    this.ndots = other.ndots;
    this.rotateServers = other.rotateServers;
    this.roundRobinInetAddress = other.roundRobinInetAddress;
  }

  public AddressResolverOptions(JsonObject json) {
    this();
    AddressResolverOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the path to the alternate hosts configuration file
   */
  public String getHostsPath() {
    return hostsPath;
  }

  /**
   * Set the path of an alternate hosts configuration file to use instead of the one provided by the os.
   * <p/>
   * The default value is null, so the operating system hosts config is used.
   *
   * @param hostsPath the hosts path
   * @return a reference to this, so the API can be used fluently
   */
  public AddressResolverOptions setHostsPath(String hostsPath) {
    this.hostsPath = hostsPath;
    return this;
  }

  /**
   * @return the hosts configuration file value
   */
  public Buffer getHostsValue() {
    return hostsValue;
  }

  /**
   * Set an alternate hosts configuration file to use instead of the one provided by the os.
   * <p/>
   * The value should contain the hosts content literaly, for instance <i>127.0.0.1 localhost</i>
   * <p/>
   * The default value is null, so the operating system hosts config is used.
   *
   * @param hostsValue the hosts content
   * @return a reference to this, so the API can be used fluently
   */
  public AddressResolverOptions setHostsValue(Buffer hostsValue) {
    this.hostsValue = hostsValue;
    return this;
  }

  /**
   * @return the hosts configuration refresh period in millis
   */
  public int getHostsRefreshPeriod() {
    return hostsRefreshPeriod;
  }

  /**
   * Set the hosts configuration refresh period in millis, {@code 0} disables it.
   * <p/>
   * The resolver caches the hosts configuration {@link #hostsPath file} after it has read it. When
   * the content of this file can change, setting a positive refresh period will load the configuration
   * file again when necessary.
   *
   * @param hostsRefreshPeriod the hosts configuration refresh period
   */
  public AddressResolverOptions setHostsRefreshPeriod(int hostsRefreshPeriod) {
    if (hostsRefreshPeriod < 0) {
      throw new IllegalArgumentException("hostsRefreshPeriod must be >= 0");
    }
    this.hostsRefreshPeriod = hostsRefreshPeriod;
    return this;
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
  public AddressResolverOptions setServers(List<String> servers) {
    this.servers = servers;
    return this;
  }

  /**
   * Add a DNS server address.
   *
   * @param server the server to add
   * @return a reference to this, so the API can be used fluently
   */
  public AddressResolverOptions addServer(String server) {
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
  public AddressResolverOptions setOptResourceEnabled(boolean optResourceEnabled) {
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
  public AddressResolverOptions setCacheMinTimeToLive(int cacheMinTimeToLive) {
    if (cacheMinTimeToLive < 0) {
      throw new IllegalArgumentException("cacheMinTimeToLive must be >= 0");
    }
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
  public AddressResolverOptions setCacheMaxTimeToLive(int cacheMaxTimeToLive) {
    if (cacheMaxTimeToLive < 0) {
      throw new IllegalArgumentException("cacheMaxTimeToLive must be >= 0");
    }
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
  public AddressResolverOptions setCacheNegativeTimeToLive(int cacheNegativeTimeToLive) {
    if (cacheNegativeTimeToLive < 0) {
      throw new IllegalArgumentException("cacheNegativeTimeToLive must be >= 0");
    }
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
  public AddressResolverOptions setQueryTimeout(long queryTimeout) {
    if (queryTimeout < 1) {
      throw new IllegalArgumentException("queryTimeout must be > 0");
    }
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
  public AddressResolverOptions setMaxQueries(int maxQueries) {
    if (maxQueries < 1) {
      throw new IllegalArgumentException("maxQueries must be > 0");
    }
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
  public AddressResolverOptions setRdFlag(boolean rdFlag) {
    this.rdFlag = rdFlag;
    return this;
  }

  /**
   * @return the list of search domains
   */
  public List<String> getSearchDomains() {
    return searchDomains;
  }

  /**
   * Set the lists of DNS search domains.
   * <p/>
   * When the search domain list is null, the effective search domain list will be populated using
   * the system DNS search domains.
   *
   * @param searchDomains the search domains
   */
  public AddressResolverOptions setSearchDomains(List<String> searchDomains) {
    this.searchDomains = searchDomains;
    return this;
  }

  /**
   * Add a DNS search domain.
   *
   * @param searchDomain the search domain to add
   * @return a reference to this, so the API can be used fluently
   */
  public AddressResolverOptions addSearchDomain(String searchDomain) {
    if (searchDomains == null) {
      searchDomains = new ArrayList<>();
    }
    searchDomains.add(searchDomain);
    return this;
  }

  /**
   * @return the ndots value
   */
  public int getNdots() {
    return ndots;
  }

  /**
   * Set the ndots value used when resolving using search domains, the default value is {@code -1} which
   * determines the value from the OS on Linux or uses the value {@code 1}.
   *
   * @param ndots the new ndots value
   * @return a reference to this, so the API can be used fluently
   */
  public AddressResolverOptions setNdots(int ndots) {
    if (ndots < -1) {
      throw new IllegalArgumentException("ndots must be >= -1");
    }
    this.ndots = ndots;
    return this;
  }

  /**
   * @return the value {@code true} when the dns server selection uses round robin
   */
  public boolean isRotateServers() {
    return rotateServers;
  }

  /**
   * Set to {@code true} to enable round-robin selection of the dns server to use. It spreads the query load
   * among the servers and avoids all lookup to hit the first server of the list.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public AddressResolverOptions setRotateServers(boolean rotateServers) {
    this.rotateServers = rotateServers;
    return this;
  }

  /**
   * @return the value {@code true} when the inet address selection uses round robin
   */
  public boolean isRoundRobinInetAddress() {
    return roundRobinInetAddress;
  }

  /**
   * Set to {@code true} to enable round-robin inet address selection of the ip address to use.
   *
   * @return a reference to this, so the API can be used fluently
   */
  public AddressResolverOptions setRoundRobinInetAddress(boolean roundRobinInetAddress) {
    this.roundRobinInetAddress = roundRobinInetAddress;
    return this;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    AddressResolverOptionsConverter.toJson(this, json);
    return json;
  }
}
