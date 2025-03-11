/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.http.MimeMapping;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MimeMappingTest {

  @Test
  public void testUpperCase() {
    assertEquals("image/jpeg", MimeMapping.mimeTypeForFilename("FOO.JPG"));
    assertEquals("image/jpeg", MimeMapping.mimeTypeForFilename("FOO.jpg"));
    assertEquals("image/jpeg", MimeMapping.mimeTypeForFilename("FOO.jPG"));
    assertEquals("image/jpeg", MimeMapping.mimeTypeForFilename("FOO.JpG"));
  }
}
