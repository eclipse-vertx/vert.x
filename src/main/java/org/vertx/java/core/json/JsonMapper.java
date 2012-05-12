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
package org.vertx.java.core.json;

import java.util.ServiceLoader;

import org.vertx.java.core.VertxException;

/**
 * This is a quick & dirty way of removing the API dependency on
 * the Jackson implementation, without having to refactor a 
 * bunch of other classes
 * 
 * @author pidster
 *
 */
public class JsonMapper {
	
	private static JsonMapperDelegate delegate;
	
	static {
		ServiceLoader<JsonMapperDelegate> factories = ServiceLoader.load(JsonMapperDelegate.class);
		try {
			delegate = factories.iterator().next();
	    } catch (Exception e) {
	    	throw new VertxException(e);
	    }
	}
	
	public static String encode(Object obj) throws EncodeException {
		return delegate.encode(obj);
	}

	public static Object decodeValue(String str, Class<?> clazz) throws DecodeException {
		return delegate.decodeValue(str, clazz);
	}

}
