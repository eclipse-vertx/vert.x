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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ContainerNode;
import org.codehaus.jackson.node.ObjectNode;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.utils.Args;
import org.vertx.java.core.utils.StringUtils;
import org.vertx.java.core.utils.SystemUtils;

/**
 * A factory to load the VertxConfig. The standard process to load VertxConfig is as follows:
 * <ol>
 * <li>Determine the config file name either from <code>-Dvertx.config</code>, the env variable 
 * 	<code>VERTX_CONFIG_FILE</code> or use '<code>vertx.cfg</code>'. In that order. The first non-null value 
 * 	will be used. </li>
 * <li>Use the factories classloader to load the json file</li>
 * <li>Load the system-default from '<code>vertx-default.cfg</code>'</li>
 * <li>Replace missing entries in the user config with system-defaults</code>'</li>
 * <li>Replace placeholder with real values. See below for more details.</code>'</li>
 * </ol>
 * <p>
 * <b>Placeholder</b> in the json config file<p>
 * An example entry may look like this: <code>modRoot: [ "${prop:vertx.mods}", "${env:VERTX_MODS}", "mods" ]</code><p/>
 * Placeholders are the <code>${selector:value}<code> entries. Currently 3 selectors are supported.
 * <ul>
 * <li>prop: will be resolved via System.getProperty(value)</li>
 * <li>env: will be resolved via System.getenv(value)</li>
 * <li>file: the json root object created out of the file (value == filename) will replace the placeholder</li>
 * </ul>
 * 
 * @author Juergen Donnerstag
 */
public class VertxConfigFactory {

	private static final Logger log = LoggerFactory.getLogger(VertxConfigFactory.class);
	
	private static final ObjectMapper mapper;
	
	static {
    mapper = new ObjectMapper();
 	 	mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);  
 	 	mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
 	 	// TODO I wish Jason would support something like ALLOW_UNQUOTED_VALUE
 	 	// TODO I wish Jason would support something like ALLOW_TOLERANT_COMMA_SEPARATOR and 
 	 	//   treat end-of-line as separator as well.
	}

	/**
	 * Factory: load the config 
	 */
	public VertxConfig load() {
 	  // TODO why not conf/vertx.cfg? To put it into the path seems a bit awkward
 	 	String configFile = SystemUtils.systemVar("vertx.config", "VERTX_CONFIG_FILE", "vertx.cfg");
		URL url = getClass().getClassLoader().getResource(configFile);
		if (url == null) {
			throw new RuntimeException("Vertx config file not found: " + configFile);
		}
		try {
			return load(url.toURI(), false, true);
		} catch (URISyntaxException ignore) {
			log.error("Ups. How can that happen?!", ignore);
		}
		return null;
	}
	
	/**
	 * Factory
	 */
	public final VertxConfig load(final File file, boolean throwException, boolean createEmptyOnError) {
		Args.notNull(file, "file");
		return load(file.toURI(), throwException, createEmptyOnError);
	}

	/**
	 * Factory
	 */
	public final VertxConfig load(final URI url, boolean throwException, boolean createEmptyOnError) {
		Args.notNull(url, "url");
		JsonNode node = null;
		File file = new File(url);
		try {
			node = mapper.readTree(file);
		} catch (IOException ex) {
			log.error("Error while reading config " + url.toString(), ex);
			if (throwException) {
				throw new RuntimeException("Unable to read vertx config file: " + file.getAbsolutePath(), ex);
			}
			
			if (createEmptyOnError) {
				node = mapper.createObjectNode();
			}
		}
		return _newVertxConfig(node);
	}

	/**
	 * Factory. Mostly for testing
	 */
	public final VertxConfig load(final String json) throws IOException {
		Args.notNull(json, "json");
		JsonNode node = mapper.readTree(json);
		return _newVertxConfig(node);
	}

	/**
	 * 
	 */
	private VertxConfig _newVertxConfig(JsonNode node) {
		if (node == null) {
			return null;
		}
		
		node = merge(node, loadDefault());
		replacePlaceholder((ContainerNode)node);
		return newVertxConfig(node);
	}

	/**
	 * Extension point: subclass to provide your modified version of VertxConfig
	 */
	protected VertxConfig newVertxConfig(JsonNode node) {
		return new VertxConfig(node);
	}

	/**
	 * Used recursively to iterate over all elements of a node. If a string and if the string 
	 * contains a placeholder, than replace it.
	 * 
	 * @param node
	 */
	private void replacePlaceholder(final ContainerNode node) {
		Iterator<Map.Entry<String, JsonNode>> iter = node.getFields();
		while (iter.hasNext()) {
			Map.Entry<String, JsonNode> entry = iter.next();
			JsonNode value = reviewNodeEntry(node, entry.getKey(), entry.getValue());
			if (value != null) {
				((ObjectNode)node).put(entry.getKey(), value);
			}
		}
	}

	/**
	 * Analyze a specific node, if is an array, an object (with sub-nodes), or a plain value field.
	 * 
	 * @param parent
	 * @param name
	 * @param node
	 * @return
	 */
	private JsonNode reviewNodeEntry(final ContainerNode parent, final String name, final JsonNode node) {
		if (node.isArray()) {
			ArrayNode ar = (ArrayNode) node;
			for (int i=0; i < node.size(); i++) {
				JsonNode n = node.get(i);
				JsonNode value = reviewNodeEntry(ar, null, n);
				if (value != null) {
					i = handleArrayUpdate(ar, i, value);
				}
			}
		} else if (node.isObject()) {
			replacePlaceholder((ContainerNode)node);
		} else if (node.isTextual()) {
			String val = node.getTextValue();
			if (val != null) {
				val = val.trim();
				if (val.isEmpty() == false) {
					if (val.startsWith("${") && val.endsWith("}")) {
						val = val.substring(2, val.length() - 1);
						return handlePlaceholder(parent, val);
					}
				}
			}
		}
		return null;
	}

	/**
	 * We found a placeholder and now need to handle it.
	 * 
	 * @param parent
	 * @param val
	 * @return
	 */
	protected JsonNode handlePlaceholder(final ContainerNode parent, String val) {
		String[] entries = val.split(",");
		for (String entry: entries) {
			String[] data = entry.trim().split(":");
			if (data.length == 2) {
				val = resolvePlaceholder(data[0], data[1]);
				if (val != null && val.trim().length() != 0) {
					try {
						return mapper.readTree(val);
					} catch (IOException ignore) {
					}
					return parent.textNode(val);
				}
			} else {
				return parent.textNode(entry.trim());
			}
		}
		return parent.nullNode();
	}

	/**
	 * Update the placeholder array element with one or more array elements provided
	 * via the placeholder.
	 */
	protected final int handleArrayUpdate(final ArrayNode ar, int i, final JsonNode value) {
		if (value.isNull()) {
			ar.remove(i--);
		} else {
			ar.remove(i--);
			String[] entries = value.getTextValue().split(", ");
			for (String e: entries) {
				ar.insert(++i, ar.textNode(e.trim()));
			}
		}
		return i;
	}

	/**
	 * Extension point: Resolve the placeholder
	 */
	protected String resolvePlaceholder(final String key, final String val) {
		if (key.equals("prop")) {
			return systemProperty(val);
		} else if (key.equals("env")) {
			return envVariable(val);
		} else if (key.equals("file")) {
			return getFileContent(val);
 		} else {
			log.error("Unknown selector '" + key + "' in config file. Value: " + val);
		}
		return val;
	}

	/**
	 * Extensible for testing only: fake system properties
	 * 
	 * @param key
	 * @return
	 */
	protected String systemProperty(final String key) {
		return System.getProperty(key);
	}
	
	/**
	 * Extensible for testing only: fake environment variables
	 * 
	 * @param key
	 * @return
	 */
	protected String envVariable(final String key) {
		return System.getenv(key);
	}

	protected String getFileContent(final String file) {
		Args.notNull(file, "file");
		File f = new File(file);
	  try {
	    return StringUtils.loadFromFile(f);
	  } catch (IOException ex) {
	  	log.error("File not found: " + f);
	  }
    return null;
	}
	
	/**
	 * @return Create an empty json object
	 */
	public static final ObjectNode createEmptyJson() {
		return mapper.createObjectNode();
	}

	/**
	 * @return Load the system-wide default file
	 */
	protected final JsonNode loadDefault() {
		String name = "vertx-default.cfg";
		URL url = getClass().getResource(name);
		if (url == null) {
			throw new RuntimeException("Ups. vertx system default config not found: " + name);
		}
		try {
			return mapper.readTree(url);
		} catch (IOException ex) {
			throw new RuntimeException("Ups. Unable to read vertx system default config: " + url.toString(), ex);
		}
	}
	
	/**
	 * Replace missing entries in 'node' with defaults from 'defs'. Recursive.
	 */
	protected JsonNode merge(final JsonNode node, final JsonNode defs) {
		Args.notNull(node, "node");
		Args.notNull(defs, "defs");
		
		Iterator<String> fieldNames = defs.getFieldNames();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      JsonNode n = node.get(fieldName);
      JsonNode def = defs.get(fieldName);
      if (n != null && def != null && n.getClass() != def.getClass()) {
      	log.error("Vertx config file parameter might be of wrong json type. Expected: '" 
      			+ def.getClass().getSimpleName() + "', found: '" 
      			+ n.getClass().getSimpleName() + "'");
      }
      if (n != null && n.isObject()) {
      	merge(n, defs.get(fieldName));
      }
      else if (n == null && node instanceof ObjectNode) {
        ((ObjectNode) node).put(fieldName, def);
      }
    }
    return node;
	}
}
