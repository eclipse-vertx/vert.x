package io.vertx.test.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a JUnit @Before/@BeforeClass/@Test method parameter to declare a {@link VertxProvider} responsible
 * for creating and destroying an instance of {@link io.vertx.core.Vertx}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface ProvidedBy {

  /**
   * @return the provider
   */
  Class<? extends VertxProvider> value();

}
