package io.vertx.test.proxy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.METHOD})
public @interface WithProxy {

  String username() default "";

  ProxyKind kind();

  String[] localhosts() default "";

}
