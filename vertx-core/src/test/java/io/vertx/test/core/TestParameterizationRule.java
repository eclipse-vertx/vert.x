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
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
      Field field = locateTestDataField(target, parameterized);
      Method method0 = locateTestDataMethod(target, parameterized);
      List<Object> testData = invoke(target, method0);
      result = new ParameterizedTestStatement(target, statement, field, testData);
    }
    return result;
  }

  private static Field locateTestDataField(Object target, TestParameterization parameterized) {
    Field field = null;
    for (Field field0 : getAccessibleFields(target)) {
      if (field0.isAnnotationPresent(TestParameterization.TestDataField.class) &&
        field0.getAnnotation(TestParameterization.TestDataField.class).value().equals(parameterized.targetField())) {
        field = field0;
        break;
      }
    }
    if (field == null) {
      throw new RuntimeException("Field annotated with " + TestParameterization.TestDataField.class +
        " and named '" + parameterized.targetField() + "' not found.");
    }
    return field;
  }

  private static Method locateTestDataMethod(Object target, TestParameterization parameterized) {
    Method method = null;
    for (Method method0 : getAccessibleMethods(target)) {
      if (method0.isAnnotationPresent(TestParameterization.TestDataMethod.class) &&
        method0.getAnnotation(TestParameterization.TestDataMethod.class).value().equals(parameterized.dataMethod())) {
        method = method0;
        break;
      }
    }

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

  private static List<Field> getAccessibleFields(Object target) {
    List<Field> fields = new LinkedList<>();
    Class<?> clazz = target.getClass();
    while (clazz != null && clazz != Object.class) {
      for (Field field : clazz.getDeclaredFields()) {
        if (isMemberAccessible(target.getClass(), field)) {
          fields.add(field);
        }
      }
      clazz = clazz.getSuperclass();
    }
    return fields;
  }

  private static List<Method> getAccessibleMethods(Object target) {
    List<Method> methods = new LinkedList<>();
    Class<?> clazz = target.getClass();
    while (clazz != null && clazz != Object.class) {
      for (Method method : clazz.getDeclaredMethods()) {
        if (isMemberAccessible(target.getClass(), method)) {
          methods.add(method);
        }
      }
      clazz = clazz.getSuperclass();
    }
    return methods;
  }

  private static <T> T invoke(Object target, Method method, Object... args) {
    try {
      return (T) method.invoke(target, args);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean isMemberAccessible(Class<?> clazz, Member member) {
    if (Modifier.isPublic(member.getModifiers())) {
      return true;
    }
    if (Modifier.isProtected(member.getModifiers()) && isSamePackage(clazz, member)) {
      return true;
    }
    if (Modifier.isPrivate(member.getModifiers()) && clazz.equals(member.getDeclaringClass())) {
      return true;
    }
    return false;
  }

  private static boolean isSamePackage(Class<?> clazz, Member field) {
    Package classPackage = clazz.getPackage();
    Package fieldPackage = field.getDeclaringClass().getPackage();
    return classPackage != null && classPackage.equals(fieldPackage);
  }
}
