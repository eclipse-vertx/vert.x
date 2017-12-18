/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.launcher.commands;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Utility methods to test path matching. This is used by the {@link Watcher} to determine whether or not a file
 * triggers a redeployment.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public final class FileSelector {

  /**
   * When the pattern starts with a {@link File#separator}, {@code str} has to start with a {@link File#separator}.
   *
   * @return {@code true} when @{code str} starts with a {@link File#separator}, and {@code pattern} starts with a
   * {@link File#separator}.
   */
  private static boolean separatorPatternStartSlashMismatch(String pattern, String str, String separator) {
    return str.startsWith(separator) != pattern.startsWith(separator);
  }

  /**
   * Tests whether or not a given path matches a given pattern.
   *
   * @param pattern The pattern to match against. Must not be
   *                {@code null}.
   * @param str     The path to match, as a String. Must not be
   *                {@code null}.
   * @return {@code true} if the pattern matches against the string,
   * or {@code false} otherwise.
   */
  public static boolean matchPath(String pattern, String str) {
    return matchPath(pattern, str, true);
  }

  /**
   * Tests whether or not a given path matches a given pattern.
   *
   * @param pattern         The pattern to match against. Must not be
   *                        {@code null}.
   * @param str             The path to match, as a String. Must not be
   *                        {@code null}.
   * @param isCaseSensitive Whether or not matching should be performed
   *                        case sensitively.
   * @return {@code true} if the pattern matches against the string,
   * or {@code false} otherwise.
   */
  public static boolean matchPath(String pattern, String str, boolean isCaseSensitive) {
    return matchPath(pattern, str, File.separator, isCaseSensitive);
  }

  protected static boolean matchPath(String pattern, String str, String separator, boolean isCaseSensitive) {
    return matchPathPattern(pattern, str, separator, isCaseSensitive);
  }

  private static boolean matchPathPattern(String pattern, String str, String separator, boolean isCaseSensitive) {
    if (separatorPatternStartSlashMismatch(pattern, str, separator)) {
      return false;
    }
    String[] patDirs = tokenizePathToString(pattern, separator);
    String[] strDirs = tokenizePathToString(str, separator);
    return matchPathPattern(patDirs, strDirs, isCaseSensitive);

  }

  private static boolean matchPathPattern(String[] patDirs, String[] strDirs, boolean isCaseSensitive) {
    int patIdxStart = 0;
    int patIdxEnd = patDirs.length - 1;
    int strIdxStart = 0;
    int strIdxEnd = strDirs.length - 1;

    // up to first '**'
    while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
      String patDir = patDirs[patIdxStart];
      if (patDir.equals("**")) {
        break;
      }
      if (!match(patDir, strDirs[strIdxStart], isCaseSensitive)) {
        return false;
      }
      patIdxStart++;
      strIdxStart++;
    }
    if (strIdxStart > strIdxEnd) {
      // String is exhausted
      for (int i = patIdxStart; i <= patIdxEnd; i++) {
        if (!patDirs[i].equals("**")) {
          return false;
        }
      }
      return true;
    } else {
      if (patIdxStart > patIdxEnd) {
        // String not exhausted, but pattern is. Failure.
        return false;
      }
    }

    // up to last '**'
    while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
      String patDir = patDirs[patIdxEnd];
      if (patDir.equals("**")) {
        break;
      }
      if (!match(patDir, strDirs[strIdxEnd], isCaseSensitive)) {
        return false;
      }
      patIdxEnd--;
      strIdxEnd--;
    }
    if (strIdxStart > strIdxEnd) {
      // String is exhausted
      for (int i = patIdxStart; i <= patIdxEnd; i++) {
        if (!patDirs[i].equals("**")) {
          return false;
        }
      }
      return true;
    }

    while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
      int patIdxTmp = -1;
      for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
        if (patDirs[i].equals("**")) {
          patIdxTmp = i;
          break;
        }
      }
      if (patIdxTmp == patIdxStart + 1) {
        // '**/**' situation, so skip one
        patIdxStart++;
        continue;
      }
      // Find the pattern between padIdxStart & padIdxTmp in str between
      // strIdxStart & strIdxEnd
      int patLength = (patIdxTmp - patIdxStart - 1);
      int strLength = (strIdxEnd - strIdxStart + 1);
      int foundIdx = -1;
      strLoop:
      for (int i = 0; i <= strLength - patLength; i++) {
        for (int j = 0; j < patLength; j++) {
          String subPat = patDirs[patIdxStart + j + 1];
          String subStr = strDirs[strIdxStart + i + j];
          if (!match(subPat, subStr, isCaseSensitive)) {
            continue strLoop;
          }
        }

        foundIdx = strIdxStart + i;
        break;
      }

      if (foundIdx == -1) {
        return false;
      }

      patIdxStart = patIdxTmp;
      strIdxStart = foundIdx + patLength;
    }

    for (int i = patIdxStart; i <= patIdxEnd; i++) {
      if (!patDirs[i].equals("**")) {
        return false;
      }
    }

    return true;
  }

  /**
   * Tests whether or not a string matches against a pattern.
   * The pattern may contain two special characters:<br>
   * '*' means zero or more characters<br>
   * '?' means one and only one character
   *
   * @param pattern The pattern to match against.
   *                Must not be{@code null}.
   * @param str     The string which must be matched against the pattern.
   *                Must not be{@code null}.
   * @return {@code true} if the string matches against the pattern,
   * or {@code false} otherwise.
   */
  public static boolean match(String pattern, String str) {
    return match(pattern, str, true);
  }

  /**
   * Tests whether or not a string matches against a pattern.
   * The pattern may contain two special characters:<br>
   * '*' means zero or more characters<br>
   * '?' means one and only one character
   *
   * @param pattern         The pattern to match against.
   *                        Must not be{@code null}.
   * @param str             The string which must be matched against the pattern.
   *                        Must not be{@code null}.
   * @param isCaseSensitive Whether or not matching should be performed
   *                        case sensitively.
   * @return {@code true} if the string matches against the pattern,
   * or {@code false} otherwise.
   */
  public static boolean match(String pattern, String str, boolean isCaseSensitive) {
    char[] patArr = pattern.toCharArray();
    char[] strArr = str.toCharArray();
    return match(patArr, strArr, isCaseSensitive);
  }

  private static boolean match(char[] patArr, char[] strArr, boolean isCaseSensitive) {
    int patIdxStart = 0;
    int patIdxEnd = patArr.length - 1;
    int strIdxStart = 0;
    int strIdxEnd = strArr.length - 1;
    char ch;

    boolean containsStar = false;
    for (char aPatArr : patArr) {
      if (aPatArr == '*') {
        containsStar = true;
        break;
      }
    }

    if (!containsStar) {
      // No '*'s, so we make a shortcut
      if (patIdxEnd != strIdxEnd) {
        return false; // Pattern and string do not have the same size
      }
      for (int i = 0; i <= patIdxEnd; i++) {
        ch = patArr[i];
        if (ch != '?' && !equals(ch, strArr[i], isCaseSensitive)) {
          return false; // Character mismatch
        }
      }
      return true; // String matches against pattern
    }

    if (patIdxEnd == 0) {
      return true; // Pattern contains only '*', which matches anything
    }

    // Process characters before first star
    while ((ch = patArr[patIdxStart]) != '*' && strIdxStart <= strIdxEnd) {
      if (ch != '?' && !equals(ch, strArr[strIdxStart], isCaseSensitive)) {
        return false; // Character mismatch
      }
      patIdxStart++;
      strIdxStart++;
    }
    if (strIdxStart > strIdxEnd) {
      return checkOnlyStartsLeft(patArr, patIdxStart, patIdxEnd);
    }

    // Process characters after last star
    while ((ch = patArr[patIdxEnd]) != '*' && strIdxStart <= strIdxEnd) {
      if (ch != '?' && !equals(ch, strArr[strIdxEnd], isCaseSensitive)) {
        return false; // Character mismatch
      }
      patIdxEnd--;
      strIdxEnd--;
    }
    if (strIdxStart > strIdxEnd) {
      // All characters in the string are used. Check if only '*'s are
      // left in the pattern. If so, we succeeded. Otherwise failure.
      return checkOnlyStartsLeft(patArr, patIdxStart, patIdxEnd);
    }

    // process pattern between stars. padIdxStart and patIdxEnd point
    // always to a '*'.
    while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
      int patIdxTmp = -1;
      for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
        if (patArr[i] == '*') {
          patIdxTmp = i;
          break;
        }
      }
      if (patIdxTmp == patIdxStart + 1) {
        // Two stars next to each other, skip the first one.
        patIdxStart++;
        continue;
      }
      // Find the pattern between padIdxStart & padIdxTmp in str between
      // strIdxStart & strIdxEnd
      int patLength = (patIdxTmp - patIdxStart - 1);
      int strLength = (strIdxEnd - strIdxStart + 1);
      int foundIdx = -1;
      strLoop:
      for (int i = 0; i <= strLength - patLength; i++) {
        for (int j = 0; j < patLength; j++) {
          ch = patArr[patIdxStart + j + 1];
          if (ch != '?' && !equals(ch, strArr[strIdxStart + i + j], isCaseSensitive)) {
            continue strLoop;
          }
        }

        foundIdx = strIdxStart + i;
        break;
      }

      if (foundIdx == -1) {
        return false;
      }

      patIdxStart = patIdxTmp;
      strIdxStart = foundIdx + patLength;
    }

    // All characters in the string are used. Check if only '*'s are left
    // in the pattern. If so, we succeeded. Otherwise failure.
    return checkOnlyStartsLeft(patArr, patIdxStart, patIdxEnd);
  }

  private static boolean checkOnlyStartsLeft(char[] patArr, int patIdxStart, int patIdxEnd) {
    // All characters in the string are used. Check if only '*'s are
    // left in the pattern. If so, we succeeded. Otherwise failure.
    for (int i = patIdxStart; i <= patIdxEnd; i++) {
      if (patArr[i] != '*') {
        return false;
      }
    }
    return true;
  }

  /**
   * Tests whether two characters are equal.
   */
  private static boolean equals(char c1, char c2, boolean isCaseSensitive) {
    if (c1 == c2) {
      return true;
    }
    if (!isCaseSensitive) {
      // NOTE: Try both upper case and lower case as done by String.equalsIgnoreCase()
      if (Character.toUpperCase(c1) == Character.toUpperCase(c2)
          || Character.toLowerCase(c1) == Character.toLowerCase(c2)) {
        return true;
      }
    }
    return false;
  }

  private static String[] tokenizePathToString(String path, String separator) {
    List<String> ret = new ArrayList<>();
    StringTokenizer st = new StringTokenizer(path, separator);
    while (st.hasMoreTokens()) {
      ret.add(st.nextToken());
    }
    return ret.toArray(new String[ret.size()]);
  }
}
