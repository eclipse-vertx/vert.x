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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static io.vertx.test.core.ReflectionUtils.*;

public class TestParameterizationRule implements MethodRule {

  private static class ParameterizedTestStatement extends Statement {

    private final Statement statement;
    private final Field testDataField;
    private final List<Object> testData;
    private final Object target;

    private ParameterizedTestStatement(Object target, Statement statement, Field testDataField, List<Object> testData) {
      this.target = target;
      this.statement = statement;
      this.testDataField = testDataField;
      this.testData = testData;
    }

    @Override
    public void evaluate() throws Throwable {
      for (int i = 0; i < testData.size(); i++) {
        System.out.println("*** Iteration " + (i + 1) + "/" + testData.size() + " of test");
        testDataField.set(target, testData.get(i));
        statement.evaluate();
      }
    }
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod method, Object target) {
    Statement result = statement;
    TestParameterization parameterized = method.getAnnotation(TestParameterization.class);
    if (parameterized != null) {
      Field field = locateTestDataField(target, parameterized);
      Method method0 = locateTestDataMethod(target, parameterized);
      List<Object> testData = invoke(target, method0);
      result = new ParameterizedTestStatement(target, statement, field, testData);
    }
    return result;
  }

  private static Field locateTestDataField(Object target, TestParameterization parameterized) {
    Field field = getTestDataField(target, parameterized);
    if (field == null) {
      throw new RuntimeException("Field annotated with " + TestParameterization.TestDataField.class +
        " and named '" + parameterized.targetField() + "' not found.");
    }
    field.trySetAccessible();
    return field;
  }

  private static Field getTestDataField(Object target, TestParameterization parameterized) {
    for (Field field0 : getAccessibleFields(target)) {
      if (field0.isAnnotationPresent(TestParameterization.TestDataField.class) &&
        field0.getAnnotation(TestParameterization.TestDataField.class).value().equals(parameterized.targetField())) {
        return field0;
      }
    }
    return null;
  }

  private static Method locateTestDataMethod(Object target, TestParameterization parameterized) {
    Method method = getTestDataMethod(target, parameterized);

    if (method == null) {
      throw new RuntimeException("Method annotated with " + TestParameterization.TestDataMethod.class + " and named '"
        + parameterized.dataMethod() + "' not found.");
    }
    if (method.getReturnType() != List.class) {
      throw new RuntimeException("Test data method must return a List. Method '" + method.getName() + "' returns " +
        method.getReturnType().getName());
    }
    if (method.getParameterCount() != 0) {
      throw new RuntimeException("Test data method should not have parameters. Method '" + method.getName() + "' " +
        "has " + method.getParameterCount() + " parameter(s).");
    }
    method.trySetAccessible();
    return method;
  }

  private static Method getTestDataMethod(Object target, TestParameterization parameterized) {
    for (Method method0 : getAccessibleMethods(target)) {
      if (method0.isAnnotationPresent(TestParameterization.TestDataMethod.class) &&
        method0.getAnnotation(TestParameterization.TestDataMethod.class).value().equals(parameterized.dataMethod())) {
        return method0;
      }
    }
    return null;
  }
}
