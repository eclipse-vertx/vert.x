package org.vertx.java.framework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a test class annotated with @RunWith(VerticleTestRunner.class), the static methods annotated as @BeforeStart
 * are called before the verticle instance is created and deployed. They can be used, for example, to deploy additional
 * verticles and modules (e.g. the verticle under test).
 *
 * A method annotated as @BeforeStart can either have no parameters or have one parameter of type
 * {@link VertxTestFixture}.
 *
 * @author yole
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BeforeStart {
}
