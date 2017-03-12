/*
 * Copyright (c) 2011-2017 The original author or authors
 * ------------------------------------------------------
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

package io.vertx.core.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Resolver options.
 *
 * @author Thomas Segismont
 */
public class ResolvConf {

  public static final ResolvConf DEFAULT = new ResolvConf(1, false);

  private final int ndots;
  private final boolean rotate;

  public ResolvConf(int ndots, boolean rotate) {
    this.ndots = ndots;
    this.rotate = rotate;
  }

  public int getNdots() {
    return ndots;
  }

  public boolean isRotate() {
    return rotate;
  }

  /**
   * Parses {@code resolv.conf} options.
   *
   * @param env  comma separated list of options, retrieved from environment (per-process override)
   * @param path path to the {@code resolv.conf} file
   * @return options parsed
   */
  public static ResolvConf load(String env, String path) {
    Map<String, String> options;

    if (env != null) {
      options = Arrays.stream(env.split("\\s+"))
        .collect(HashMap::new, ResolvConf::addOption, HashMap::putAll);
    } else if (path != null) {
      File f = new File(path);
      if (!f.isFile() || !f.canRead()) {
        return DEFAULT;
      }
      try (Stream<String> lines = Files.lines(f.toPath())) {
        options = lines.map(line -> line.trim().split("\\s+"))
          .filter(tokens -> tokens.length > 1 && tokens[0].equals("options"))
          .flatMap(tokens -> Arrays.stream(tokens).skip(1))
          .collect(HashMap::new, ResolvConf::addOption, HashMap::putAll);
      } catch (IOException ignore) {
        return DEFAULT;
      }
    } else {
      return DEFAULT;
    }

    String ndotsStr = options.get("ndots");
    int ndots;
    try {
      ndots = ndotsStr != null ? Integer.parseInt(ndotsStr) : 1;
    } catch (NumberFormatException ignore) {
      return DEFAULT;
    }
    boolean rotate = options.containsKey("rotate");
    return new ResolvConf(ndots, rotate);
  }

  private static void addOption(Map<String, String> map, String option) {
    String[] split = option.split(":");
    if (split.length == 1) {
      map.put(split[0], null);
    } else if (split.length == 2) {
      map.put(split[0], split[1]);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResolvConf that = (ResolvConf) o;
    return ndots == that.ndots && rotate == that.rotate;
  }

  @Override
  public int hashCode() {
    int result = ndots;
    result = 31 * result + (rotate ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ResolvConf{" + "ndots=" + ndots + ", rotate=" + rotate + '}';
  }
}
