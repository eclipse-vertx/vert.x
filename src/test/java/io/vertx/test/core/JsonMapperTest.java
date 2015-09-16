/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.Json;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonMapperTest extends VertxTestBase {

  @Test
  public void testGetSetMapper() {
    ObjectMapper mapper = Json.mapper;
    assertNotNull(mapper);
    ObjectMapper newMapper = new ObjectMapper();
    Json.mapper = newMapper;
    assertSame(newMapper, Json.mapper);
    Json.mapper = mapper;
  }

  @Test
  public void testGetSetPrettyMapper() {
    ObjectMapper mapper = Json.prettyMapper;
    assertNotNull(mapper);
    ObjectMapper newMapper = new ObjectMapper();
    Json.prettyMapper = newMapper;
    assertSame(newMapper, Json.prettyMapper);
    Json.prettyMapper = mapper;
  }
}
