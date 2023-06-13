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

package io.vertx.core.impl.logging;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.junit.After;
import org.junit.Test;

import static io.vertx.core.impl.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;

/**
 * Must be separated from {@link LoggingBackendSelectionTest}.
 * Otherwise, the Vert.x instance might be fully initialized before our test runs.
 *
 * @author Thomas Segismont
 */
public class NoExceptionInInitializerErrorTest {

  @After
  public void tearDown() {
    System.clearProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME);
  }

  @Test
  public void doTest() throws Exception {
    System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");
    // Will fail if:
    //  - the logging impl uses Vertx static methods (e.g. currentContext in a converter)
    //  - io.vertx.core.logging.LoggerFactory logs something before being fully initialized
    Vertx.vertx();
  }

  @Plugin(name = "VertxContextConverter", category = PatternConverter.CATEGORY)
  @ConverterKeys("vsc")
  public static class VertxContextConverter extends LogEventPatternConverter {

    private VertxContextConverter(String[] options) {
      super("vsc", "vsc");
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
      // We simply want to make sure we can only get an initialized Vert.x instance
      toAppendTo.append(Vertx.currentContext());
    }

    public static VertxContextConverter newInstance(final String[] options) {
      return new VertxContextConverter(options);
    }
  }
}
