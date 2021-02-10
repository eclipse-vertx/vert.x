/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.executor;

import static io.vertx.core.spi.executor.Utils.EXECUTE;
import static io.vertx.core.spi.executor.Utils.SHUTDOWN_NOW;

import java.util.concurrent.ExecutorService;

import org.junit.ClassRule;
import org.junit.rules.TestRule;

import io.vertx.core.BlockedThreadCheckerTest;

/**
 * This test class checks that the blocked thread checker functionality of
 * Vert.x is not impacted by using a service loaded
 * {@link io.vertx.core.spi.executor.ExecutorServiceFactory} to create an
 * {@link ExecutorService} that is then used to run the blocked worker tasks.
 * 
 * It performs the same tests as BlockedThreadCheckerTest but using an external
 * {@link java.util.concurrent.ExecutorService}
 * 
 * To enable different tests to have particular ExecutorServiceFactory's (or
 * none), we only put the particular ExecutorServiceFactory .class file on the
 * classpath and make it service loadable as part of each test and are careful
 * to remove it afterwards.
 */
public class ExecutorFactoryBlockedThreadCheckerTest extends BlockedThreadCheckerTest {

  private static final Class<ExternalLoadableExecutorServiceFactory> FACTORY = ExternalLoadableExecutorServiceFactory.class;
  private static final String EXECUTOR = FACTORY.getSimpleName() + "$1";

  /*
   * We use a Junit test rule to setup and tear down the classpath to have the
   * correct ExecutorServiceFactory SPI implementation and then check the logs
   * after the tests are run. That means we can inherit all the test setup, tests
   * and tear down from the parent class. We pass in the factory, the name of the
   * executor class that should appear in the logs and a ... of the methods we
   * expect to be used.
   */
  @ClassRule
  public static TestRule chain = Utils.setupAndCheckSpiImpl(FACTORY, EXECUTOR, EXECUTE, SHUTDOWN_NOW);

}
