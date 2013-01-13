/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.codehaus.jackson.JsonNode;
import org.vertx.java.core.impl.VertxConfig;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.Args;

/**
 * A little helper to manage proxy config data such as proxy host, user name and password.
 * But also lists of internal (no proxy required) and external (via proxy) servers (resp. server pattern).
 * <p>
 * <b>External</b> servers should go <b>via the proxy</b> and thus should go on the <b>'include'</b> list.<br/>
 * <b>Internal</b> servers <b>don't require a proxy</b> and thus should go on the <b>'exclude'</b> list.
 * 
 * @author Juergen Donnerstag
 */
public class ProxyConfig {

	private static final Logger log = LoggerFactory.getLogger(ProxyConfig.class);

	public static final String JSON_PROXY_NODE = "proxy";
	
  // --------- System Properties --------------
	// TODO remove. use vertx-defaults
	
  public static final String HTTP_PROXY_HOST__OLD__PROP_NAME = "http.proxyHost";
  public static final String HTTP_PROXY_PORT__OLD__PROP_NAME = "http.proxyPort";

  // E.g. "http://username:password@foo.bar:8080"
  public static final String HTTP_PROXY_HOST_PROP_NAME = "proxy_host";

  // user and password can be provided separately
  public static final String HTTP_PROXY_USER_PROP_NAME = "proxy_user";
  public static final String HTTP_PROXY_PASSWORD_PROP_NAME = "proxy_password";

  // Comma separated list of internal servers where the proxy must _not_ be used. 
  // Supports pattern like "*.mycompany.com"
  public static final String HTTP_PROXY_INTERNALS_PROP_NAME = "proxy_internals";

  // Comma separated list of external servers where the proxy should be used. 
  // Supports pattern like "*.github.com"
  public static final String HTTP_PROXY_EXTERNALS_PROP_NAME = "proxy_externals";

  // --------- Environment Variables --------------

  public static final String HTTP_PROXY_HOST_ENV_NAME = "VERTX_HTTP_PROXY_HOST";
  public static final String HTTP_PROXY_PORT_ENV_NAME = "VERTX_HTTP_PROXY_PORT";

  public static final String HTTP_PROXY_USER_ENV_NAME = "VERTX_PROXY_USER";
  public static final String HTTP_PROXY_PASSWORD_ENV_NAME = "VERTX_PROXY_PASSWORD";
  
  public static final String HTTP_PROXY_INTERNALS_ENV_NAME = "VERTX_PROXY_INTERNALS";
  public static final String HTTP_PROXY_EXTERNALS_ENV_NAME = "VERTX_PROXY_EXTERNALS";

  // --------- JSON parameters --------------

  public static final String HOST = "host";
  public static final String USER = "user";
  public static final String PASSWORD = "password";
  public static final String ENABLED = "enabled";
  public static final String DEFAULT_TO_INTERNAL = "defaultToInternal";
  public static final String EXTERNAL = "external";
  public static final String INTERNAL  = "internal";

  // E.g. "http://username:password@foo.bar:8080"
  private String host;

	private final List<Pattern> externalServers = new ArrayList<>();
	private final List<Pattern> internalServers = new ArrayList<>();

	private JsonNode root;
	private boolean enabled;
	
	// Allows to configure the matcher. If true, than servers are regarded internal
	// unless listed in the include (external server) list.
	private boolean defaultToInternal;
	
  /**
   * Constructor
   * 
   * @param The json root node for the proxy config parameters. Must not be null. 
   * 	See {@link VertxConfig#createEmptyJson()} to create an empty json object.
   */
  public ProxyConfig(final JsonNode root) {
  	this.root = Args.notNull(root, "root");
  	this.host = initHost();
  	this.enabled = root.path(ENABLED).asBoolean(true);
  	this.defaultToInternal = root.path(DEFAULT_TO_INTERNAL).asBoolean(true);
  	
  	initExternalAndInternal(this.externalServers, this.internalServers);
  }

  /**
   * Get the string value of a subnode to the proxy root node. The subnote must contain a string
   * value. If the node does not exist, return null.
   * 
   * @param path
   * @return
   */
  private String getParam(final String path) {
  	JsonNode node = root.path(path);
  	if (!node.isMissingNode()) {
  		return node.getTextValue();
  	}
  	return null;
  }
  
  /**
   * Extension point: Initialize the proxy host value
   * 
   * @return
   */
	protected String initHost() {
  	return root.path(HOST).path(0).asText();
	}

	/**
	 * Extension point: Initialize user and password
	 */
	protected void initUser() {
  	String user = getParam(USER);
  	String password = getParam(PASSWORD);
  	
 		user(user, password);
	}

  /**
   * Extension Point: Initialize the include and exclude lists
   * 
   * @param externals
   * @param internals
   */
	protected void initExternalAndInternal(final List<Pattern> externals, final List<Pattern> internals) {
  	JsonNode node = root.path(EXTERNAL);
  	if (!node.isMissingNode() && node.isArray() == false) {
  		throw new IllegalArgumentException("Vertx config parameter 'proxy/" + EXTERNAL + "' must be an Array");
  	}

  	List<String> ins = new ArrayList<>();
  	for (int i=0; i < node.size(); i++) {
  		ins.add(node.get(i).getTextValue());
  	}

  	node = root.path(INTERNAL);
  	if (!node.isMissingNode() && node.isArray() == false) {
  		throw new IllegalArgumentException("Vertx config parameter 'proxy." + INTERNAL + "' must be an Array");
  	}

  	List<String> exs = new ArrayList<>();
  	for (int i=0; i < node.size(); i++) {
  		exs.add(node.get(i).getTextValue());
  	}

  	convertToPattern(ins, externals, false);
  	convertToPattern(exs, internals, false);
	}
  
	/**
	 * Convert the provided include and exclude strings into a list of regex
	 * 
	 * @param val
	 * @param list
	 * @param clear
	 */
  protected final void convertToPattern(final List<String> val, final List<Pattern> list, final boolean clear) {
  	if (clear) {
  		list.clear();
  	}
  	if (val != null) {
	  	for(String v: val) {
	  		v = trim(v);
	  		v = v.replaceAll("[.]", "[.]");
	  		v = v.replaceAll("[*]", ".*?");
	  		v = "(.*?[@])?" + v;
	  		if (v.contains("://") == false) {
	  			v = ".*?[:]//" + v;
	  		}
	  		list.add(Pattern.compile(v));
	  	}
  	}
  }
  
	/**
	 * Trim the string. If empty afterwards, return null
	 * 
	 * @param val
	 * @return
	 */
  protected final String trim(String val) {
  	if (val == null) {
  		return null;
  	}
  	
  	val = val.trim();
  	if (val.length() == 0) {
  		return null;
  	}
  	
  	return val;
  }

  /**
   * @return The configured proxy address (e.g. http://www.myproxy.com:8181). 
   * 		Returns null if the proxy has been disabled.
   */
  public final String proxy() {
  	return enabled ? this.host : null;
  }

  /**
   * @return The proxy host name (e.g. www.myproxy.com) without scheme, user, password and port.
   */
  public final String proxyHost() {
  	URI uri = proxyUri();
  	if (uri != null) {
  		return uri.getHost();
  	}
  	return null;
  }
  
  /**
   * @return The proxy port number (e.g. 8181)
   */
  public final int proxyPort() {
  	URI uri = proxyUri();
  	if (uri != null) {
  		return uri.getPort();
  	}
  	return 0;
  }
  
  private URI proxyUri() {
  	if (enabled && this.host != null) {
	  	try {
				return new URI(this.host);
			} catch (URISyntaxException ex) {
				log.error("Invalid Proxy host URL: " + this.host, ex);
			}
  	}
  	return null;
  }
  
  /**
   * Replace the existing proxy host string, including user and password
   * 
   * @param host E.g. "http://username:password@foo.bar:8080"
   * @return
   */
  public final ProxyConfig setProxyHost(String host) {
  	if (host == null) {
  		this.enabled = false;
  		this.host = null;
    	log.info("Disabled proxy");
  	} else {
  		this.enabled = true;
  		if (!host.contains("://")) {
  			host = "http://" + host;
  		}
  		this.host = host;
    	log.info("Reset proxy: " + host);
  	}
  	return this;
  }

  /**
   * Change proxy host to include user and password
   * 
   * @param user
   * @param password
   * @return
   */
  public final ProxyConfig user(String user, final String password) {
  	if (user != null) {
  		if (password != null) {
  			user += ":" + password;
  		}
  		this.host.replaceFirst("://(.*?@)?", "://" + user + "@");
  	}
  	
  	return this;
  }

  /**
   * Add or replace the list of external hosts which need to pass via the proxy.
   * 
   * @param externals Comma separated list of hosts (e.g. *.github.com)
   * @param clear If true, delete the current list 
   * @return
   */
  public ProxyConfig externalServers(final String externals, final boolean clear) {
	  convertToPattern(externals, this.externalServers, clear);
  	return this;
  }

  /**
   * Add or replace a list of internal hosts which do NOT need to pass via the proxy
   * 
   * @param internals Comma separated list of hosts (e.g. *.github.com)
   * @param clear If true, delete the current list 
   * @return
   */
  public ProxyConfig internalServers(final String internals, final boolean clear) {
  	convertToPattern(internals, this.internalServers, clear);
  	return this;
  }

	/**
	 * Convert the provided include/exclude strings into a list of regex
	 * 
	 * @param val
	 * @param list
	 * @param clear
	 */
  protected final void convertToPattern(final String val, final List<Pattern> pattern, final boolean clear) {
  	String[] data = null;
  	if (val != null && val.trim().length() != 0) {
	  	data = val.split(",");
  	}
  	List<String> list = Collections.emptyList();
  	if (data != null && data.length != 0) {
  		list = Arrays.asList(data);
  	}
  	convertToPattern(list, pattern, clear);
  }
  
  protected boolean matchesExternals(final String target) {
  	if (defaultToInternal == false && externalServers.size() == 0) {
  		return true;
  	}
  	return matches(target, externalServers);
  }
  
  protected boolean matchesInternals(final String target) {
  	if (defaultToInternal && internalServers.size() == 0) {
  		return true;
  	}
  	return matches(target, internalServers);
  }
  
  protected final boolean matches(final String target, final List<Pattern> pattern) {
  	for (Pattern p: pattern) {
  		if (p.matcher(target).matches() == true) {
  			return true;
  		}
  	}
  	return false;
  }

  /**
   * Validate if a target host is internal (no proxy) or external (proxy).
   * 
   * @param target
   * @param defaultAnswer If both include and exclude are either both true or false, than defaultAnswer gets returned
   * @return
   */
  public boolean requiresProxy(final String target, final boolean defaultAnswer) {
  	if (this.enabled == false) {
  		return false;
  	}
  	
  	if (this.host != null) {
	  	boolean internal = matchesInternals(target);  // internal servers (no proxy)
	  	boolean external = matchesExternals(target);  // external servers (via proxy)
	  	if (internal == true && external == false) {
	  		return false;
	  	} else if (internal == false && external == true) {
	  		return true;
	  	} 
	  
	  	return defaultAnswer;
  	}
  	return this.defaultToInternal == false;
  }
  
  /**
   * This little helper is completely independent from ProxyConfig. It merely supports a common
   * use case where in case a proxy is required and the target-Uri does not contain the target
   * host, that the target host gets prepended to the original uri.
   * 
   * @return
   */
  public String updateTargetUri(String target, final String protocol, final String host, 
  		final int port, final String user, final String password) {
  	
  	if (target.contains("://")) {
  		return target;
  	}

  	StringBuilder newHost = new StringBuilder(300);
  	newHost.append(protocol == null ? "http" : protocol);
  	newHost.append("://");
  	if (user != null) {
  		newHost.append(user);
	  	if (password != null) {
	  		newHost.append(":").append(password);
	  	}
	  	newHost.append("@");
  	}
  	newHost.append(host);
  	if (port != 0) {
  		newHost.append(":").append(port);
  	}
  	if (target.startsWith("/") == false) {
  		newHost.append("/");
  	}
  	newHost.append(target);
  	
  	return newHost.toString();
  }

  public final ProxyConfig enabled(boolean enabled) {
  	this.enabled = enabled;
  	return this;
  }
  
  public final boolean enabled() {
  	return this.enabled;
  }

  public final ProxyConfig defaultToInternal(boolean val) {
  	this.defaultToInternal = val;
  	return this;
  }
  
  public final boolean defaultToInternal() {
  	return this.defaultToInternal;
  }
  
  /**
   * @return Proxy setting in form of 'host:port'
   */
  @Override
  public String toString() {
  	return "Proxy: " + host + "; enabled: " + enabled + "; defaultToInternal: " + defaultToInternal 
  			+ " external: " + externalServers + "; internal: " + internalServers;
  }
}
