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

package org.vertx.java.tests.core.datagram;

import org.junit.Test;
import org.vertx.java.testframework.TestBase;
import vertx.tests.core.datagram.TestClient;


/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class JavaDatagramTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testEchoBound() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testConfigureAfterBind() throws Exception {
    startTest(getMethodName());
  }

  @Test
  public void testMulticastJoinLeave() throws Exception {
    startTest(getMethodName());
  }

  /**
   * Not support on OSX.. Need to check on which OS it works.
   *
   * java.lang.UnsupportedOperationException
   * at sun.nio.ch.DatagramChannelImpl.block(DatagramChannelImpl.java:941)
   * at sun.nio.ch.MembershipKeyImpl.block(MembershipKeyImpl.java:183)
  @Test
  public void testMulticastJoinBlock() throws Exception {
    startTest(getMethodName());
  }
  */
}
