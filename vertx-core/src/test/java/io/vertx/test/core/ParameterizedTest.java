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

package io.vertx.test.core;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static io.vertx.test.core.ReflectionUtils.*;

public class ParameterizedTest {

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  public @interface Parameterized {
    String parameters();

    String parameter();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface Parameters {
    String value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface Parameter {
    String value();
  }

  public static class Rule implements MethodRule {

    private static class ParameterizedTestStatement extends Statement {
      private final Statement statement;
      private final Field parameter;
      private final List<Object> parameters;
      private final Object target;

      private ParameterizedTestStatement(Object target, Statement statement, Field parameter, List<Object> parameters) {
        this.target = target;
        this.statement = statement;
        this.parameter = parameter;
        this.parameters = parameters;
      }

      @Override
      public void evaluate() throws Throwable {
        for (int i = 0; i < parameters.size(); i++) {
          System.out.println("*** Iteration " + (i + 1) + "/" + parameters.size() + " of test");
          parameter.set(target, parameters.get(i));
          statement.evaluate();
        }
      }
    }

    @Override
    public Statement apply(Statement statement, FrameworkMethod method, Object target) {
      Statement result = statement;
      Parameterized parameterized = method.getAnnotation(Parameterized.class);
      if (parameterized != null) {
        Field field = locateTestParameterMember(target, parameterized);
        Method method0 = locateTestParametersMember(target, parameterized);
        List<Object> parameters = invoke(target, method0);
        result = new ParameterizedTestStatement(target, statement, field, parameters);
      }
      return result;
    }

    private static Field locateTestParameterMember(Object instance, Parameterized parameterized) {
      Field parameter = getTestParameterMember(instance, parameterized);
      if (parameter == null) {
        throw new RuntimeException("Member annotated with " + Parameter.class +
          " and named '" + parameterized.parameter() + "' not found.");
      }
      parameter.trySetAccessible();
      return parameter;
    }

    private static Field getTestParameterMember(Object instance, Parameterized parameterized) {
      for (Field field0 : getAccessibleFields(instance)) {
        if (field0.isAnnotationPresent(Parameter.class) &&
          field0.getAnnotation(Parameter.class).value().equals(parameterized.parameter())) {
          return field0;
        }
      }
      return null;
    }

    private static Method locateTestParametersMember(Object instance, Parameterized parameterized) {
      Method method = getTestParametersMember(instance, parameterized);

      if (method == null) {
        throw new RuntimeException("Method annotated with " + Parameters.class + " and named '"
          + parameterized.parameters() + "' not found.");
      }
      if (method.getReturnType() != List.class) {
        throw new RuntimeException("Method must return a List. Method '" + method.getName() + "' returns " +
          method.getReturnType().getName());
      }
      if (method.getParameterCount() != 0) {
        throw new RuntimeException("Method should not have parameters. Method '" + method.getName() + "' " +
          "has " + method.getParameterCount() + " param(s).");
      }
      method.trySetAccessible();
      return method;
    }

    private static Method getTestParametersMember(Object instance, Parameterized parameterized) {
      for (Method method0 : getAccessibleMethods(instance)) {
        if (method0.isAnnotationPresent(Parameters.class) &&
          method0.getAnnotation(Parameters.class).value().equals(parameterized.parameters())) {
          return method0;
        }
      }
      return null;
    }
  }
}
