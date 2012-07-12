/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * Modified from original form by Tim Fox
 */
package org.vertx.java.core.logging.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.LogRecord;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class VertxLoggerFormatter extends java.util.logging.Formatter {
  private static String LINE_SEPARATOR = System.getProperty("line.separator");

  @Override
  public String format(final LogRecord record) {
    Date date = new Date();
    SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss,SSS");
    StringBuffer sb = new StringBuffer();
    // Minimize memory allocations here.
    date.setTime(record.getMillis());
    sb.append("[").append(Thread.currentThread().getName()).append("] ");
    sb.append(dateFormat.format(date)).append(" ");
    sb.append(record.getLevel()).append(" [");
    sb.append(record.getLoggerName()).append("]").append("  ");
    sb.append(record.getMessage());

    sb.append(VertxLoggerFormatter.LINE_SEPARATOR);
    if (record.getThrown() != null) {
      try {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        record.getThrown().printStackTrace(pw);
        pw.close();
        sb.append(sw.toString());
      } catch (Exception ex) {
      }
    }
    return sb.toString();
  }

}
