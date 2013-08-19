/*
 * Copyright 2013 the original author or authors.
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
}
