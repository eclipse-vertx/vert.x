/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * Represents the execution context of a Verticle.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Context {

  void runOnContext(Handler<Void> action);

  String deploymentID();

  JsonObject config();

  List<String> processArgs();

}
