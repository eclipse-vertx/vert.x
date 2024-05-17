package io.vertx.test.core;

import io.vertx.core.Expectation;

import java.util.function.Consumer;

public class AssertExpectations {

  public static <T> Expectation<T> that(Consumer<? super T> consumer) {
    return value -> {
      consumer.accept(value);
      return true;
    };
  }


//  public static <T> Expectation<T> assertThat(Matcher<? super T> matcher) {
//    return assertThat("", matcher);
//  }
//
//  public static <T> Expectation<T> assertThat(String reason, Matcher<? super T> matcher) {
//    return new Expectation<T>() {
//      @Override
//      public boolean test(T actual) {
//        return matcher.matches(actual);
//      }
//      @Override
//      public Throwable describe(T actual) {
//        Description description = new StringDescription();
//        description.appendText(reason)
//          .appendText("\nExpected: ")
//          .appendDescriptionOf(matcher)
//          .appendText("\n     but: ");
//        matcher.describeMismatch(actual, description);
//        return new AssertionError(description.toString());
//      }
//    };
//  }

}
