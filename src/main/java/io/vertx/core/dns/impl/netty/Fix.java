package io.vertx.core.dns.impl.netty;

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.vertx.core.dns.impl.netty.UnixResolverDnsServerAddressStreamProvider.parseEtcResolverFirstNdots;

public class Fix {

  public static final List<String> DEFAULT_SEARCH_DOMAINS;
  private static final int DEFAULT_NDOTS;

  static {
    String[] searchDomains;
    try {
      List<String> list = PlatformDependent.isWindows()
        ? getSearchDomainsHack()
        : UnixResolverDnsServerAddressStreamProvider.parseEtcResolverSearchDomains();
      searchDomains = list.toArray(new String[0]);
    } catch (Exception ignore) {
      // Failed to get the system name search domain list.
      searchDomains = EmptyArrays.EMPTY_STRINGS;
    }
    DEFAULT_SEARCH_DOMAINS = Collections.unmodifiableList(Arrays.asList(searchDomains));

    int ndots;
    try {
      ndots = parseEtcResolverFirstNdots();
    } catch (Exception ignore) {
      ndots = UnixResolverDnsServerAddressStreamProvider.DEFAULT_NDOTS;
    }
    DEFAULT_NDOTS = ndots;
  }

  private static List<String> getSearchDomainsHack() throws Exception {
    // This code on Java 9+ yields a warning about illegal reflective access that will be denied in
    // a future release. There doesn't seem to be a better way to get search domains for Windows yet.
    Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
    Method open = configClass.getMethod("open");
    Method nameservers = configClass.getMethod("searchlist");
    Object instance = open.invoke(null);

    return (List<String>) nameservers.invoke(instance);
  }
}
