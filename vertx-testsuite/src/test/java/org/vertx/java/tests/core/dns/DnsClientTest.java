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
package org.vertx.java.tests.core.dns;


import org.junit.Test;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.dns.DnsTestClient;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DnsClientTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(DnsTestClient.class.getName());
  }

  @Test
  public void testResolveA() {
    startTest(getMethodName());
  }

  @Test
  public void testResolveAAAA() {
    startTest(getMethodName());
  }

  @Test
  public void testResolveMX() {
    startTest(getMethodName());
  }

  @Test
  public void testResolveTXT() {
    startTest(getMethodName());
  }

  @Test
  public void testResolveNS() {
    startTest(getMethodName());
  }

  @Test
  public void testResolveCNAME() {
    startTest(getMethodName());
  }

  @Test
  public void testResolvePTR() {
    startTest(getMethodName());
  }

  @Test
  public void testResolveSRV() {
    startTest(getMethodName());
  }

  @Test
  public void testLookup6() {
    startTest(getMethodName());
  }

  @Test
  public void testLookup4() {
    startTest(getMethodName());
  }

  @Test
  public void testLookup() {
    startTest(getMethodName());
  }

  @Test
  public void testLookupNonExisting() {
    startTest(getMethodName());
  }

  @Test
  public void testReverseLookupIpv4() {
    startTest(getMethodName());
  }

  @Test
  public void testReverseLookupIpv6() {
    startTest(getMethodName());
  }
}
