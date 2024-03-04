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
package io.vertx.core;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An expectation, very much like a predicate with the ability to provide a meaningful description of the failure.
 * <p/>
 * Expectation can be used with {@link Future#expecting(Expectation)}.
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@FunctionalInterface
public interface Expectation<V> {

  /**
   * Check the {@code value}, this should be side effect free, the expectation should either succeed returning {@code true} or fail
   * returning {@code false}.
   *
   * @param value the checked value
   */
  boolean test(V value);

  /**
   * Returned an expectation succeeding when this expectation and the {@code other} expectation succeeds.
   *
   * @param other the other expectation
   * @return an expectation that is a logical and of this and {@code other}
   */
  default Expectation<V> and(Expectation<? super V> other) {
    Objects.requireNonNull(other);
    return new Expectation<V>() {
      @Override
      public boolean test(V value) {
        return Expectation.this.test(value) && other.test(value);
      }
      @Override
      public Throwable describe(V value) {
        if (!Expectation.this.test(value)) {
          return Expectation.this.describe(value);
        } else if (!other.test(value)) {
          return other.describe(value);
        }
        return null;
      }
    };
  }

  /**
   * Returned an expectation succeeding when this expectation or the {@code other} expectation succeeds.
   *
   * @param other the other expectation
   * @return an expectation that is a logical or of this and {@code other}
   */
  default Expectation<V> or(Expectation<? super V> other) {
    Objects.requireNonNull(other);
    return new Expectation<V>() {
      @Override
      public boolean test(V value) {
        return Expectation.this.test(value) || other.test(value);
      }
      @Override
      public Throwable describe(V value) {
        if (Expectation.this.test(value)) {
          return null;
        } else if (other.test(value)) {
          return null;
        } else {
          return Expectation.this.describe(value);
        }
      }
    };
  }

  /**
   * Turn an invalid {@code value} into an exception describing the failure, the default implementation returns a generic exception.
   *
   * @param value the value to describe
   * @return a meaningful exception
   */
  default Throwable describe(V value) {
    return new VertxException("Unexpected result: " + value, true);
  }

  /**
   * Returns a new expectation with the same predicate and a customized error {@code descriptor}.
   *
   * @param descriptor the function describing the error
   * @return a new expectation describing the error with {@code descriptor}
   */
  default Expectation<V> wrappingFailure(BiFunction<V, Throwable, Throwable> descriptor) {
    class CustomizedExpectation implements Expectation<V> {
      private final BiFunction<V, Throwable, Throwable> descriptor;
      private CustomizedExpectation(BiFunction<V, Throwable, Throwable> descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor);
      }
      @Override
      public boolean test(V value) {
        return Expectation.this.test(value);
      }
      @Override
      public Throwable describe(V value) {
        Throwable err = Expectation.this.describe(value);
        return descriptor.apply(value, err);
      }
      @Override
      public Expectation<V> wrappingFailure(BiFunction<V, Throwable, Throwable> descriptor) {
        return new CustomizedExpectation(descriptor);
      }
    }
    return new CustomizedExpectation(descriptor);
  }
}
