package io.vertx.test.core;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;

public class ReflectionUtils {
  public static List<Field> getAccessibleFields(Object instance) {
    List<Field> fields = new LinkedList<>();
    Class<?> clazz = instance.getClass();
    while (clazz != null && clazz != Object.class) {
      for (Field field : clazz.getDeclaredFields()) {
        if (isMemberAccessible(instance.getClass(), field)) {
          fields.add(field);
        }
      }
      clazz = clazz.getSuperclass();
    }
    return fields;
  }

  public static List<Method> getAccessibleMethods(Object instance) {
    List<Method> methods = new LinkedList<>();
    Class<?> clazz = instance.getClass();
    while (clazz != null && clazz != Object.class) {
      for (Method method : clazz.getDeclaredMethods()) {
        if (isMemberAccessible(instance.getClass(), method)) {
          methods.add(method);
        }
      }
      clazz = clazz.getSuperclass();
    }
    return methods;
  }

  public static <T> T invoke(Object instance, Method method, Object... args) {
    try {
      return (T) method.invoke(instance, args);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean isMemberAccessible(Class<?> clazz, Member member) {
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

  public static boolean isSamePackage(Class<?> clazz, Member field) {
    Package classPackage = clazz.getPackage();
    Package fieldPackage = field.getDeclaringClass().getPackage();
    return classPackage != null && classPackage.equals(fieldPackage);
  }

}
