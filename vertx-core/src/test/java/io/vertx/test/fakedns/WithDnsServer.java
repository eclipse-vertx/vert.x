package io.vertx.test.fakedns;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface WithDnsServer {

  /**
   * @return the server port
   */
  int port() default 53530;

  /**
   * @return the array of records the dns server responds.
   */
  DnsRecord[] records();

}
