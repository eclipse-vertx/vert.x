package io.vertx.core.dns.impl;

import io.netty.util.internal.PlatformDependent;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddressResolverConstants {

  private static Pattern resolvOption(String regex) {
    return Pattern.compile("^[ \\t\\f]*options[^\n]+" + regex + "(?=$|\\s)", Pattern.MULTILINE);
  }

  private static final Pattern NDOTS_OPTIONS_PATTERN = resolvOption("ndots:[ \\t\\f]*(\\d)+");
  private static final Pattern ROTATE_OPTIONS_PATTERN = resolvOption("rotate");
  public static final int DEFAULT_NDOTS_RESOLV_OPTION;
  public static final boolean DEFAULT_ROTATE_RESOLV_OPTION;

  static {
    int ndots = 1;
    boolean rotate = false;
    boolean isLinux = "linux".equals(PlatformDependent.normalizedOs());
    if (isLinux) {
      File f = new File("/etc/resolv.conf");
      try {
        if (f.exists() && f.isFile()) {
          String conf = new String(Files.readAllBytes(f.toPath()));
          int ndotsOption = parseNdotsOptionFromResolvConf(conf);
          if (ndotsOption != -1) {
            ndots = ndotsOption;
          }
          rotate = parseRotateOptionFromResolvConf(conf);
        }
      } catch (Throwable t) {
        System.err.println("Failed to load options from /etc/resolv/.conf");
        t.printStackTrace();
      }
    }
    DEFAULT_NDOTS_RESOLV_OPTION = ndots;
    DEFAULT_ROTATE_RESOLV_OPTION = rotate;
  }

  public static int parseNdotsOptionFromResolvConf(String s) {
    int ndots = -1;
    Matcher matcher = NDOTS_OPTIONS_PATTERN.matcher(s);
    while (matcher.find()) {
      ndots = Integer.parseInt(matcher.group(1));
    }
    return ndots;
  }

  public static boolean parseRotateOptionFromResolvConf(String s) {
    Matcher matcher = ROTATE_OPTIONS_PATTERN.matcher(s);
    return matcher.find();
  }
}
