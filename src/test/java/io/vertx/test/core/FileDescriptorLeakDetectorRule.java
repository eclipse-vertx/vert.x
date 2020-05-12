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

import com.sun.management.UnixOperatingSystemMXBean;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FileDescriptorLeakDetectorRule implements TestRule {
  private static final int BASE_LINE_RUNS = 20;
  private static final int TEST_RUNS = 20;

  private final UnixOperatingSystemMXBean os;

  public FileDescriptorLeakDetectorRule() {
    OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    if (!(os instanceof UnixOperatingSystemMXBean))
      this.os = null;
    else
      this.os = (UnixOperatingSystemMXBean) os;

  }

  @Override
  public Statement apply(Statement statement, Description description) {
    DetectFileDescriptorLeaks detectFileDescriptorsLeaks = description.getAnnotation(DetectFileDescriptorLeaks.class);
    if (detectFileDescriptorsLeaks == null || os == null) {
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
        //We dont care if baseline executions leak because in the end evaluations will leak also
        //resulting in a greater average than max
        List<Long> baseLine = new ArrayList<>();
        List<Long> evaluations = new ArrayList<>();
        for (int i = 0; i < BASE_LINE_RUNS + TEST_RUNS; i++) {
          statement.evaluate();
          long openFd = os.getOpenFileDescriptorCount();
          if (i < BASE_LINE_RUNS) {
            baseLine.add(openFd);
          } else {
            evaluations.add(openFd);
          }
        }

        long maxBaseLine = getMax(baseLine);
        long averageEvaluations = getAverage(evaluations);
        assertThat(averageEvaluations).isLessThanOrEqualTo(maxBaseLine);
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
