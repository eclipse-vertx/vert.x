package io.vertx.test.fakedns;

public @interface DnsRecord {

  String name();

  String type() default "A";

  String address() default "127.0.0.1";

  int ttl() default 5000;

}
