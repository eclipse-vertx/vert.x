/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core;

import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.file.FileSystem;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.core.sockjs.SockJSServer;

/**
 * @author pidster
 *
 */
public interface VertxMXBean {

	  /**
	   * Create a TCP/SSL server
	   */
	  public abstract NetServer createNetServer();

	  /**
	   * Create a TCP/SSL client
	   */
	  public abstract NetClient createNetClient();

	  /**
	   * Create an HTTP/HTTPS server
	   */
	  public abstract HttpServer createHttpServer();

	  /**
	   * Create a HTTP/HTTPS client
	   */
	  public abstract HttpClient createHttpClient();

	  /**
	   * Create a SockJS server that wraps an HTTP server
	   */
	  public abstract SockJSServer createSockJSServer(HttpServer httpServer);

	  /**
	   * The File system object
	   */
	  public abstract FileSystem fileSystem();

	  /**
	   * The event bus
	   */
	  public abstract EventBus eventBus();

	  /**
	   * The shared data object
	   */
	  public abstract SharedData sharedData();

}
