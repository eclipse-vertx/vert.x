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

package io.vertx.core.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class FileUtils {

  private FileUtils() {}

  @SafeVarargs
  static long getLastModifiedTimestamp(List<String>... filePaths) {
    List<String> combinedFilePaths = Stream.of(filePaths)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

    boolean pathIsPresent = combinedFilePaths.stream()
      .filter(Objects::nonNull)
      .noneMatch(String::isEmpty);

    if (!pathIsPresent) {
      return 0;
    }

    return combinedFilePaths.stream()
      .map(FileUtils::getLastModifiedTimestamp)
      .max(Long::compareTo)
      .orElse(0L);
  }

  static long getLastModifiedTimestamp(String path) {
    if (path == null || path.length() == 0) {
      return 0;
    }

    try {
      return Files.getLastModifiedTime(Paths.get(path)).toMillis();
    } catch (IOException e) {
      return 0;
    }
  }

}
