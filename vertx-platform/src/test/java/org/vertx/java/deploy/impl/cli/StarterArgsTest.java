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
package org.vertx.java.deploy.impl.cli;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;

/**
 * @author Oliver Nautsch
 */
public class StarterArgsTest {

  @Test (expected = StarterArgs.Problem.class)
  public void exceptionIfNoArgIsGiven() throws Exception {
    new StarterArgs(new String[0]);
  }

  @Test
  public void shouldParseVersion() throws Exception {
    final StarterArgs args = new StarterArgs(new String[]{"version"});

    assertThat(args.getCommand(), is("version"));
  }

  @Test
  public void shouldParseVersionCaseInsensitive() throws Exception {
    final StarterArgs args = new StarterArgs(new String[]{"VeRsIoN"});

    assertThat(args.getCommand(), is("version"));
  }

  @Test (expected = StarterArgs.Problem.class)
  public void exceptionIfCommandWithoutOperand() throws Exception {
    new StarterArgs(new String[] {"runmod"});
  }

  @Test
  public void shouldParseCommandWithOperand() throws Exception {
    final StarterArgs args = new StarterArgs(new String[]{"run", "Server.js"});

    assertThat(args.getCommand(), is("run"));
    assertThat(args.getOperand(), is("Server.js"));

    assertThat(args.isClustered(), is(false));
    assertThat(args.isWorker(), is(false));
  }

  @Test (expected = StarterArgs.Problem.class)
  public void exceptionIfInstancesWithoutNumber() throws Exception {
    new StarterArgs(new String[]{"run", "Server.js", "-instances"});
  }

  @Test
  public void shouldParseInstances() throws Exception {
    final StarterArgs args = new StarterArgs(new String[]{"run", "Server.js", "-instances", "8"});

    assertThat(args.getCommand(), is("run"));
    assertThat(args.getOperand(), is("Server.js"));
    assertThat(args.getInstances(), is(8));
  }

  @Test
  public void shouldParseCluster() throws Exception {
    final StarterArgs args = new StarterArgs(new String[]{"run", "Server.js", "-cluster"});

    assertThat(args.getCommand(), is("run"));
    assertThat(args.getOperand(), is("Server.js"));
    assertThat(args.isClustered(), is(true));
    assertThat(args.getClusterHost(), notNullValue());
    assertThat(args.getClusterPort(), is(25500));
  }

  @Test
  public void shouldParseClusterhost() throws Exception {
    final StarterArgs args = new StarterArgs(new String[]{"run", "Server.js", "-cluster", "-cluster-host", "192.168.59.123"});

    assertThat(args.getCommand(), is("run"));
    assertThat(args.getOperand(), is("Server.js"));
    assertThat(args.isClustered(), is(true));
    assertThat(args.getClusterHost(), is("192.168.59.123"));
    assertThat(args.getClusterPort(), is(25500));
  }
  @Test
  public void shouldParseClusterport() throws Exception {
    final StarterArgs args = new StarterArgs(new String[]{"run", "Server.js", "-cluster", "-cluster-port", "25511"});

    assertThat(args.getCommand(), is("run"));
    assertThat(args.getOperand(), is("Server.js"));
    assertThat(args.isClustered(), is(true));
    assertThat(args.getClusterHost(), notNullValue());
    assertThat(args.getClusterPort(), is(25511));
  }


  @Test
  public void shouldParseWorker() throws Exception {
    final StarterArgs args = new StarterArgs(new String[]{"run", "Server.js", "-worker"});

    assertThat(args.getCommand(), is("run"));
    assertThat(args.getOperand(), is("Server.js"));
    assertThat(args.isWorker(), is(true));
  }

  @Test (expected = StarterArgs.Problem.class)
  public void exceptionIfConfWithoutConfigFileArg() throws Exception {
    new StarterArgs(new String[]{"run", "Server.js", "-conf"});
  }

  @Test
  public void shouldParseConf() throws Exception {
    final File configFile = File.createTempFile("my_vert", ".conf");

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))){
      writer.write("{\"mymode\":\"fast\"}");
    } catch (IOException ioexc){};

    configFile.deleteOnExit();

    final StarterArgs args = new StarterArgs(new String[]{"run", "Server.js", "-conf", configFile.getAbsolutePath()});

    assertThat(args.getCommand(), is("run"));
    assertThat(args.getOperand(), is("Server.js"));
    assertThat(args.getConf(), notNullValue());
  }

}
