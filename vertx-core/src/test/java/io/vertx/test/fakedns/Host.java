package io.vertx.test.fakedns;

public @interface Host {

  String name();

  String address() default "127.0.0.1";

  int ttl() default 5000;

}
