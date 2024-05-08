package io.vertx.test.fakeresolver;

import java.util.List;

public class FakeState<B> {

  final String name;
  final B endpoints;
  volatile boolean isValid;

  FakeState(String name, B endpoints) {
    this.name = name;
    this.endpoints = endpoints;
    this.isValid = true;
  }
}
