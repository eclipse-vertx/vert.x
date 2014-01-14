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

package org.vertx.java.core.logging;

import java.text.MessageFormat;
import org.vertx.java.core.logging.impl.LogDelegate;

/**
 * <p>This class allows us to isolate all our logging dependencies in one place. It also allows us to have zero runtime
 * 3rd party logging jar dependencies, since we default to JUL by default.</p>
 *
 * <p>By default logging will occur using JUL (Java-Util-Logging). The logging configuration file (logging.properties)
 * used by JUL will taken from the default logging.properties in the JDK installation if no {@code  java.util.logging.config.file} system
 * property is set. The {@code vertx-java / vertx-ruby / etc} scripts set {@code  java.util.logging.config.file} to point at the logging.properties
 * in the vertx distro install directory. This in turn configures vertx to log to a file in a directory called
 * vertx-logs in the users home directory.</p>
 *
 * <p>If you would prefer to use Log4J or SLF4J instead of JUL then you can set a system property called
 * {@code org.vertx.logger-delegate-factory-class-name} to the class name of the delegate for your logging system.
 * For Log4J the value is {@code org.vertx.java.core.logging.Log4JLogDelegateFactory}, for SLF4J the value
 * is {@code org.vertx.java.core.logging.SLF4JLogDelegateFactory}. You will need to ensure whatever jar files
 * required by your favourite log framework are on your classpath.</p>
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 */
public class Logger {

  final LogDelegate delegate;

  public Logger(final LogDelegate delegate) {
    this.delegate = delegate;
  }

  public boolean isInfoEnabled() {
    return delegate.isInfoEnabled();
  }

  public boolean isDebugEnabled() {
    return delegate.isDebugEnabled();
  }

  public boolean isTraceEnabled() {
    return delegate.isTraceEnabled();
  }

  public boolean isWarnEnabled() {
    return delegate.isWarnEnabled();
  }
  
  public boolean isFatalEnabled() {
    return delegate.isFatalEnabled();
  }
  
  public boolean isErrorEnabled() {
    return delegate.isErrorEnabled();
  }
  
  public void fatal(final Object message) {
    if (isFatalEnabled()) {
      delegate.fatal(message);
	}
  }
  
  public void fatal(final String pattern, final Object... params) {
    if (isFatalEnabled()) {
      delegate.fatal(MessageFormat.format(pattern, params));
	}
  }

  public void fatal(final Object message, final Throwable t) {
    if (isFatalEnabled()) {
      delegate.fatal(message, t);
	}
  }

  public void error(final Object message) {
    if (isErrorEnabled()) {
	  delegate.error(message);
	}
  }

  public void error(final String pattern, final Object... params) {
    if (isErrorEnabled()) {
      delegate.error(MessageFormat.format(pattern, params));
	}
  }
  
  public void error(final Object message, final Throwable t) {
    if (isErrorEnabled()) {
      delegate.error(message, t);
	}
  }

  public void warn(final Object message) {
    if (isWarnEnabled()) {
	  delegate.warn(message);
	}
  }

  public void warn(final String pattern, final Object... params) {
    if (isWarnEnabled()) {
      delegate.warn(MessageFormat.format(pattern, params));
	}
  }
  
  public void warn(final Object message, final Throwable t) {
    if (isWarnEnabled()) {
      delegate.warn(message, t);
	}
  }

  public void info(final Object message) {
    if (isInfoEnabled()) {
      delegate.info(message);
    }
  }

  public void info(final String pattern, final Object... params) {
    if (isInfoEnabled()) {
      delegate.info(MessageFormat.format(pattern, params));
	}
  }
  
  public void info(final Object message, final Throwable t) {
    if (isInfoEnabled()) {
      delegate.info(message, t);
	}
  }

  public void debug(final Object message) {
    if (isDebugEnabled()) {
      delegate.debug(message);
	}
  }

  public void debug(final String pattern, final Object... params) {
    if (isDebugEnabled()) {
      delegate.debug(MessageFormat.format(pattern, params));
	}
  }
  
  public void debug(final Object message, final Throwable t) {
    if (isDebugEnabled()) {
      delegate.debug(message, t);
	}
  }

  public void trace(final Object message) {
    if (isTraceEnabled()) {
      delegate.trace(message);
	}
  }

  public void trace(final String pattern, final Object... params) {
    if (isTraceEnabled()) {
      delegate.trace(MessageFormat.format(pattern, params));
	}
  }
  
  public void trace(final Object message, final Throwable t) {
    if (isTraceEnabled()) {
      delegate.trace(message, t);
	}
  }

}
