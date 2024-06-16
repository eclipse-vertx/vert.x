package io.vertx.test.fakeresolver;

import io.vertx.core.net.Address;

public class FakeAddress implements Address {

  private final String name;

  public FakeAddress(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof FakeAddress) {
      FakeAddress that = (FakeAddress) obj;
      return name.equals(that.name);
    }
    return false;
  }

  @Override
  public String toString() {
    return "ServiceName(" + name + ")";
  }
}
