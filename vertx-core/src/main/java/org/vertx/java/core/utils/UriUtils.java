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
package org.vertx.java.core.utils;

import java.net.URI;
import java.net.URISyntaxException;


/**
 * Collection of URI utils. 
 * 
 * @author Juergen Donnerstag
 */
public class UriUtils {

	/**
	 * Fill missing parts in 'uri' with data from 'def'
	 */
	public static URI merge(final URI uri, final URI def) throws URISyntaxException {

		String scheme = uri.getScheme() != null ? uri.getScheme() : def.getScheme();
		String user = uri.getRawUserInfo() != null ? uri.getRawUserInfo() : def.getRawUserInfo();
		String host = uri.getHost() != null ? uri.getHost() : def.getHost();
		int port = uri.getPort() != -1 ? uri.getPort() : def.getPort();
		String path = uri.getRawPath().length() > 0 ? uri.getRawPath() : def.getRawPath();
		if (path.endsWith("/")) {
			String x = def.getRawPath();
			String y = x.substring(x.indexOf("/$$module$$/") + 1);
			path += y;
		}
		String query = uri.getRawQuery() != null ? uri.getRawQuery() : def.getRawQuery();
		String fragment = uri.getRawFragment() != null ? uri.getRawFragment() : def.getRawFragment();

		return new URI(scheme, user, host, port, path, query, fragment);
	}
}
