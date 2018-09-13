/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import org.junit.Test;

import java.net.URLEncoder;

import static io.vertx.core.net.URIDecoder.*;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class URIDecoderTest {

  @Test
  public void testDecode() throws Exception {
    String original = "ein verr+++ückter text mit Leerzeichen, Plus und Umlauten";
    String encoded = URLEncoder.encode( original, "UTF-8" );
    assertEquals(original, decodeURIComponent( encoded, true ) );
  }
}
