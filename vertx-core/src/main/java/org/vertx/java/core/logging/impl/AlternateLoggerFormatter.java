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
import java.util.logging.LogRecord;

import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

/**
 * @author Juergen Donnerstag
 */
public class AlternateLoggerFormatter extends java.util.logging.Formatter {
  private static String LINE_SEPARATOR = System.getProperty("line.separator");

  private static long start;
  private String className;
  private String methodName;
  private String testMethod;
  
  @Override
  public String format(final LogRecord record) {
    StringBuffer sb = new StringBuffer();
    if (start == 0) {
    	start = record.getMillis();
    }
    sb.append(record.getMillis() - start);
    sb.append(" [").append(Thread.currentThread().getName()).append("] ");
    // sb.append(record.getLevel());
    sb.append("[");
    // sb.append(record.getLoggerName().replaceFirst(".*[.]", ""));
    // sb.append("#").append(record.getSourceMethodName());
    inferCaller();
    sb.append(testMethod).append("] [");
    sb.append(className.replaceFirst(".*[.]", ""));
    sb.append("#").append(methodName);
    sb.append("] ").append(record.getMessage());
    sb.append(AlternateLoggerFormatter.LINE_SEPARATOR);
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
  
  //Private method to infer the caller's class and method names
  private void inferCaller() {
  	JavaLangAccess access = SharedSecrets.getJavaLangAccess();
  	Throwable throwable = new Throwable();
  	int depth = access.getStackTraceDepth(throwable);

		className = null;
		methodName = null;
		testMethod = null;

  	boolean lookingForLogger = true;
  	String lastMethod = null;
  	for (int ix = 0; ix < depth; ix++) {
  		// Calling getStackTraceElement directly prevents the VM
  		// from paying the cost of building the entire stack frame.
  		StackTraceElement frame = access.getStackTraceElement(throwable, ix);
  		String cName = frame.getClassName();
  		if (lookingForLogger) {
  			// Skip all frames until we have found the first logger frame.
  			if (isLoggerImplFrame(cName)) {
  				continue;
  			} 
  			lookingForLogger = false;
  		} 
    	
  		if (lookingForLogger == false) {
				// skip reflection call
				if (cName.startsWith("java.lang.reflect.") || cName.startsWith("sun.reflect.")) {
					continue;
				}

				if (methodName == null) {
					// We've found the relevant frame.
					className = cName;
					methodName = frame.getMethodName();
				}

				if (cName.startsWith("org.junit.")) {
					testMethod = lastMethod;
					return;
				}
				
				lastMethod = frame.getMethodName();
  		}
  	}
  	// We haven't found a suitable frame, so just punt.  This is
  	// OK as we are only committed to making a "best effort" here.
 	}

	private boolean isLoggerImplFrame(String cname) {
  	// the log record could be created for a platform logger
		return (cname.equals("java.util.logging.Logger") ||
				cname.startsWith("java.util.logging.LoggingProxyImpl") ||
				cname.startsWith("sun.util.logging.") ||
				cname.startsWith("java.util.logging.") ||
				cname.startsWith("org.vertx.java.core.logging."));
	}
}
