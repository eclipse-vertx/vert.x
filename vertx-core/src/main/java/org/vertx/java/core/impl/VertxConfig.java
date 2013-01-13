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
package org.vertx.java.core.impl;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.JsonNode;
import org.vertx.java.core.http.impl.ProxyConfig;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.Args;
import org.vertx.java.core.utils.StringUtils;

/**
 * A little helper to manager vertx config data. See {@link VertxConfigFactory} for 
 * how to load/create them.
 * 
 * @author Juergen Donnerstag
 */
public class VertxConfig {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(VertxConfig.class);

	// The root node of the config data
	private final JsonNode root;

	// Lazy initialize proxy config and cache it for later use.
	private ProxyConfig proxy;
	private ModuleManagerConfig moduleManagerConfig;
	
	/**
	 * Constructor (mainly for testing)
	 * 
	 * @param json json config string
	 */
	public VertxConfig(final JsonNode json) {
		this.root = Args.notNull(json, "json");
	}

	/**
	 * @return The root json element
	 */
	public final JsonNode root() {
		return this.root;
	}

	/**
	 * @return The proxy config derived from vertx config
	 */
	public ProxyConfig proxyConfig() {
		return proxyConfig(ProxyConfig.JSON_PROXY_NODE, false);
	}

	/**
	 * @return The proxy config derived from vertx config
	 */
	public ProxyConfig proxyConfig(final String path, final boolean refresh) {
		if (proxy == null || refresh) {
			JsonNode node = null;
			if (root != null) {
				node = root.path(path);
			} 
			if (root == null || node.isMissingNode()) {
				node = VertxConfigFactory.createEmptyJson();
			}
			this.proxy = new ProxyConfig(node);
		}
		return proxy;
	}

	/**
	 * @return Get the module manager's config
	 */
	public final ModuleManagerConfig modulesManagerConfig() {
		if (this.moduleManagerConfig == null) {
			this.moduleManagerConfig = new ModuleManagerConfig(root().path("modules"));
		}
		return this.moduleManagerConfig;
	}

	public final JsonNode cmdLineConfig(String param) {
		Args.notNull(param, "param");
		return root.path("cmdLine").path(param);
	}

	public static class JsonConfig {
		private final JsonNode node;
		public final boolean enabled;
		
		JsonConfig(JsonNode node) {
			this.node = Args.notNull(node, "node");
			this.enabled = node.path("enabled").asBoolean(true);
		}
		
		public JsonNode path(String path) {
			return node.path(path);
		}
	}
	
	public static class ModuleManagerConfig extends JsonConfig {
		private String modRoot;
		public final String lib;
		public final List<RepositoryConfig> repoConfigs;
		
		ModuleManagerConfig(JsonNode node) {
			super(node);
			modRoot = path("modRoot").path(0).asText();
			if (StringUtils.isEmpty(modRoot)) {
				throw new RuntimeException("Ups. 'modRoot' should really not be null. Please update vertx-default.cfg");
			}
			lib = path("libDir").asText();
			if (StringUtils.isEmpty(lib)) {
				throw new RuntimeException("Ups. 'libDir' should really not be null. Please update vertx-default.cfg");
			}
	
			repoConfigs = new ArrayList<>();
			for (JsonNode repo: node.path("repositories")) {
				repoConfigs.add(new RepositoryConfig(repo));
			}
		}
		
		public final String modRoot() {
			return modRoot;
		}
		
		public final void modRoot(String modRoot) {
			this.modRoot = modRoot;
		}
	}
	
	public static class RepositoryConfig extends JsonConfig {
		public final String clazz;
		public final String name;
		
		RepositoryConfig(JsonNode node) {
			super(node);
			clazz = node.path("class").asText();
			if (StringUtils.isEmpty(clazz)) {
				throw new IllegalArgumentException("Module Repository 'class' parameter is missing in vertx config");
			}
			name = node.path("name").asText();
		}
	}
}
