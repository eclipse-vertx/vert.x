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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
      Field field = findTestDataField(target, parameterized);
      List<Object> testData = findTestData(target, parameterized);
      result = new ParameterizedTestStatement(target, statement, field, testData);
    }
    return result;
  }

  private static Field findTestDataField(Object target, TestParameterization parameterized) {
    Field field = locateTestDataField(target, parameterized);
    return field;
  }

  private static Field locateTestDataField(Object target, TestParameterization parameterized) {
    List<Field> fields = combine(target.getClass().getDeclaredFields(), target.getClass().getFields());
    for (Field field : fields) {
      TestParameterization.TestDataField dataFieldAnnotation =
        field.getAnnotation(TestParameterization.TestDataField.class);
      if (dataFieldAnnotation != null && dataFieldAnnotation.value().equals(parameterized.targetField())) {
        return field;
      }
    }
    throw new RuntimeException("Field annotated with " + TestParameterization.TestDataField.class +
      " and named '" + parameterized.targetField() + "' not found.");
  }

  private static List<Object> findTestData(Object target, TestParameterization parameterized) {
    Method method = locateTestDataMethod(target, parameterized);
    List<Object> testData = invoke(target, method);
    return testData;
  }

  private static Method locateTestDataMethod(Object target, TestParameterization parameterized) {
    List<Method> methods = combine(target.getClass().getDeclaredMethods(), target.getClass().getMethods());
    for (Method method : methods) {
      TestParameterization.TestDataMethod dataMethodAnnotation =
        method.getAnnotation(TestParameterization.TestDataMethod.class);
      if (dataMethodAnnotation != null && dataMethodAnnotation.value().equals(parameterized.dataMethod())) {
        return method;
      }
    }
    throw new RuntimeException("Method annotated with " + TestParameterization.TestDataMethod.class + " and named '"
      + parameterized.dataMethod() + "' not found.");
  }

  private static <T> T invoke(Object target, Method method) {
    try {
      return (T) method.invoke(target);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static <T> List<T> combine(T[] array1, T[] array2) {
    List<T> data = new LinkedList<>();
    data.addAll(Arrays.asList(array1));
    data.addAll(Arrays.asList(array2));
    return data;
  }
}
