/*
 * Copyright (c) 2009 Red Hat, Inc.
 * -------------------------------------
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

package io.vertx.core.logging.impl;

import io.vertx.core.impl.Utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.LogRecord;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class VertxLoggerFormatter extends java.util.logging.Formatter {

  @Override
  public String format(final LogRecord record) {
    OffsetDateTime date = fromMillis(record.getMillis());
    StringBuilder sb = new StringBuilder();
    // Minimize memory allocations here.
    sb.append("[").append(Thread.currentThread().getName()).append("] ");
    sb.append(date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append(" ");
    sb.append(record.getLevel()).append(" [");
    sb.append(record.getLoggerName()).append("]").append("  ");
    sb.append(record.getMessage());

    sb.append(Utils.LINE_SEPARATOR);
    if (record.getThrown() != null) {
      try {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        record.getThrown().printStackTrace(pw);
        pw.close();
        sb.append(sw.toString());
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    return sb.toString();
  }

  private static OffsetDateTime fromMillis(long epochMillis) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
  }
}
