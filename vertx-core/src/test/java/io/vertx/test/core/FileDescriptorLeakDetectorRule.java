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

package io.vertx.test.core;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public class FileDescriptorLeakDetectorRule implements TestRule {

  private static final MBeanServer MBEAN_SERVER;
  private static final ObjectName MBEAN_NAME;
  private static final MBeanAttributeInfo OPEN_FD_INFO;

  static {
    MBeanServer server = null;
    ObjectName name = null;
    MBeanAttributeInfo openFdInfo = null;
    try {
      server = ManagementFactory.getPlatformMBeanServer();
      name = ObjectName.getInstance(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
      MBeanInfo info = server.getMBeanInfo(name);
      openFdInfo = Stream
        .of(info.getAttributes())
        .filter(attrInfo ->attrInfo.getName().equals("OpenFileDescriptorCount") && attrInfo.getType().equals("long")
          && attrInfo.isReadable()).findFirst()
        .orElse(null);
    } catch (JMException ignore) {

    }
    MBEAN_SERVER = server;
    MBEAN_NAME = name;
    OPEN_FD_INFO = openFdInfo;
  }


  @Override
  public Statement apply(Statement statement, Description description) {
    DetectFileDescriptorLeaks detectFileDescriptorsLeaks = description.getAnnotation(DetectFileDescriptorLeaks.class);
    if (detectFileDescriptorsLeaks == null || OPEN_FD_INFO == null) {
      return statement;
    }
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {

        //We do 40 runs. 20 to extract a baseline and 20 that will act as evaluation values
        //From baseline values we get the max and from evaluation values we get the average
        //If average is greater than max then we have a leak.
        //The reason we do max and average is because getting file descriptors is not deterministic.
        //i.e on first run we might get 80 on the second 81 and on the third 80 again.
        //We dont care if baseline executions leak because in the end iterations will leak also
        //resulting in a greater average than max
        List<Long> baseLine = new ArrayList<>();
        long baseline = detectFileDescriptorsLeaks.baseline();
        System.out.println("*** Starting file leak descriptor test with " + baseline + " iterations");
        for (int i = 0; i < baseline; i++) {
          statement.evaluate();
          long openFd = (Long) MBEAN_SERVER.getAttribute(MBEAN_NAME, OPEN_FD_INFO.getName());
          baseLine.add(openFd);
        }
        long maxBaseLine = getMax(baseLine);
        System.out.println("*** Open file descriptor max open file descriptors " + maxBaseLine);
        List<Long> iterations = new ArrayList<>();
        long a = detectFileDescriptorsLeaks.iterations();
        for (int i = 0; i < a; i++) {
          statement.evaluate();
          long openFd = (Long) MBEAN_SERVER.getAttribute(MBEAN_NAME, OPEN_FD_INFO.getName());
          System.out.println("*** Open file descriptor iteration " + (i + 1) + "/" + a + " " + openFd + " open file descriptors");
          iterations.add(openFd);
        }

        long averageEvaluations = getAverage(iterations);
        System.out.println("*** Open file descriptor open file descriptors average " + averageEvaluations);
        assertTrue(averageEvaluations <= maxBaseLine);
      }
    };
  }

  private static long getAverage(List<Long> values) {
    return Double.valueOf(values.stream()
      .mapToLong(v -> v)
      .average()
      .orElseThrow(IllegalStateException::new)).longValue();
  }

  private static long getMax(List<Long> values) {
    return values.stream()
      .mapToLong(v -> v)
      .max()
      .orElseThrow(IllegalStateException::new);
  }
}
