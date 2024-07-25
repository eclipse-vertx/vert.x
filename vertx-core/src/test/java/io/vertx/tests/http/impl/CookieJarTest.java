package io.vertx.tests.http.impl;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.impl.CookieImpl;
import io.vertx.core.http.impl.CookieJar;
import io.vertx.core.http.impl.ServerCookie;
import org.junit.Test;

import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;

public class CookieJarTest {

  @Test
  public void testInsert() {
    CookieJar jar = new CookieJar();
    assertEquals(0, jar.size());

    jar.add(new CookieImpl("foo", "bar"));
    assertEquals(1, jar.size());

    jar.add((ServerCookie) new CookieImpl("foo", "bar").setDomain("vertx.io"));
    assertEquals(2, jar.size());

    jar.add((ServerCookie) new CookieImpl("foo", "bar").setDomain("vertx.io").setPath("/secret"));
    assertEquals(3, jar.size());
  }

  @Test
  public void testReplace() {
    CookieJar jar = new CookieJar();
    assertEquals(0, jar.size());

    jar.add(new CookieImpl("foo", "bar"));
    assertEquals(1, jar.size());

    // will replace
    jar.add(new CookieImpl("foo", "barista"));
    assertEquals(1, jar.size());

    for (ServerCookie cookie : jar) {
      assertEquals("barista", cookie.getValue());
    }
  }

  @Test
  public void testSameName() {
    CookieJar jar = new CookieJar();
    assertEquals(0, jar.size());

    jar.add(new CookieImpl("foo", "bar"));
    assertEquals(1, jar.size());

    jar.add((ServerCookie) new CookieImpl("foo", "bar").setDomain("a"));
    assertEquals(2, jar.size());

    jar.add((ServerCookie) new CookieImpl("foo", "bar").setDomain("b"));
    assertEquals(3, jar.size());

    jar.add((ServerCookie) new CookieImpl("foo", "bar").setPath("a"));
    assertEquals(4, jar.size());

    jar.add((ServerCookie) new CookieImpl("foo", "bar").setPath("b"));
    assertEquals(5, jar.size());

    jar.add((ServerCookie) new CookieImpl("foo", "bar").setPath("a").setDomain("a"));
    assertEquals(6, jar.size());

    jar.add((ServerCookie) new CookieImpl("foo", "bar").setPath("b").setDomain("b"));
    assertEquals(7, jar.size());
  }

  @Test
  public void testFilterByName() {
    CookieJar jar = new CookieJar();
    jar.add(new CookieImpl("a", "a"));
    jar.add((ServerCookie) new CookieImpl("a", "a").setPath("p"));
    jar.add(new CookieImpl("b", "b"));

    Set<Cookie> subJar = (Set) jar.getAll("a");
    assertEquals(2, subJar.size());
  }

  @Test
  public void testFilterByUniqueId() {
    CookieJar jar = new CookieJar();
    jar.add(new CookieImpl("a", "a"));
    jar.add((ServerCookie) new CookieImpl("a", "a").setPath("p"));
    jar.add(new CookieImpl("b", "b"));

    Cookie cookie = jar.get("a", null, "p");
    assertNotNull(cookie);
  }

  @Test
  public void testRemoveFromIterator() {
    CookieJar jar = new CookieJar();
    jar.add(new CookieImpl("a", "a"));
    jar.add((ServerCookie) new CookieImpl("a", "a").setPath("p"));
    jar.add(new CookieImpl("b", "b"));

    Iterator<ServerCookie> it = jar.iterator();
    assertTrue(it.hasNext());
    it.next();
    // should be allowed
    it.remove();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testRemoveFromIteratorSubJar() {
    CookieJar jar = new CookieJar();
    jar.add(new CookieImpl("a", "a"));
    jar.add((ServerCookie) new CookieImpl("a", "a").setPath("p"));
    jar.add(new CookieImpl("b", "b"));

    Set<Cookie> subJar = (Set) jar.getAll("a");

    Iterator<Cookie> it = subJar.iterator();
    assertTrue(it.hasNext());
    it.next();
    // should not be allowed
    it.remove();
  }

  @Test
  public void testPropertyPropagation() {
    CookieJar jar = new CookieJar();
    jar.add(new CookieImpl("a", "a"));

    for (ServerCookie cookie : jar.getAll("a")) {
      cookie.setValue("b");
    }

    assertEquals("b", jar.get("a").getValue());
  }
}
