package io.vertx.test.core;

import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.Http2HeadersAdaptor;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class Http2HeadersAdapterTest extends MultiMapTest {

    private DefaultHttp2Headers mheaders;

    protected MultiMap createMap() {
        return new Http2HeadersAdaptor(new DefaultHttp2Headers(false));
    }

    @Test
    public void testAddMultiMap()
            throws Exception {

        MultiMap mm = createMap();
        mm.add("Header1", "value1");
        mm.add("Header2", "value2");

        MultiMap result = mmap.addAll(mm);

        assertEquals(2, result.size());
        assertEquals("header1: value1\nheader2: value2\n", sortByLine(result.toString()));
    }

    @Test
    public void testToString() {
        assertEquals("", mmap.toString());
        mmap.add("Header1", "Value1");
        assertEquals("header1: Value1\n",
                sortByLine(mmap.toString()));
        mmap.add("Header2", "Value2");
        assertEquals("header1: Value1\n"
                        + "header2: Value2\n",
                sortByLine(mmap.toString()));
        mmap.add("Header1", "Value3");
        assertEquals("header1: Value1\n"
                        + "header1: Value3\n"
                        + "header2: Value2\n",
                sortByLine(mmap.toString()));
        mmap.remove("Header1");
        assertEquals("header2: Value2\n",
                sortByLine(mmap.toString()));
        mmap.set("Header2", "Value4");
        assertEquals("header2: Value4\n",
                sortByLine(mmap.toString()));
    }

    @Test
    public void testMapEntryToString() throws Exception {

        mmap.add("Header", "value");

        assertEquals("header: value", mmap.iterator().next().toString());
    }

    @Before
    public void setUp() {
        mheaders = new DefaultHttp2Headers(false);
        mmap = new Http2HeadersAdaptor(mheaders);
    }

    @Test
    public void testGetConvertUpperCase() {
        mmap.set("foo", "foo_value");
        assertEquals("foo_value", mmap.get("Foo"));
        assertEquals("foo_value", mmap.get((CharSequence) "Foo"));
    }

    @Test
    public void testGetAllConvertUpperCase() {
        mmap.set("foo", "foo_value");
        assertEquals(Collections.singletonList("foo_value"), mmap.getAll("Foo"));
        assertEquals(Collections.singletonList("foo_value"), mmap.getAll((CharSequence) "Foo"));
    }

    @Test
    public void testContainsConvertUpperCase() {
        mmap.set("foo", "foo_value");
        assertTrue(mmap.contains("Foo"));
        assertTrue(mmap.contains((CharSequence) "Foo"));
    }

    @Test
    public void testSetConvertUpperCase() {
        mmap.set("Foo", "foo_value");
        mmap.set((CharSequence) "Bar", "bar_value");
        mmap.set("Juu", (Iterable<String>) Collections.singletonList("juu_value"));
        mmap.set("Daa", Collections.singletonList((CharSequence) "daa_value"));
        assertHeaderNames("foo", "bar", "juu", "daa");
    }

    @Test
    public void testAddConvertUpperCase() {
        mmap.add("Foo", "foo_value");
        mmap.add((CharSequence) "Bar", "bar_value");
        mmap.add("Juu", (Iterable<String>) Collections.singletonList("juu_value"));
        mmap.add("Daa", Collections.singletonList((CharSequence) "daa_value"));
        assertHeaderNames("foo", "bar", "juu", "daa");
    }

    @Test
    public void testRemoveConvertUpperCase() {
        mmap.set("foo", "foo_value");
        mmap.remove("Foo");
        mmap.set("bar", "bar_value");
        mmap.remove((CharSequence) "Bar");
        assertHeaderNames();
    }

    private void assertHeaderNames(String... expected) {
        assertEquals(new HashSet<>(Arrays.asList(expected)), mheaders.names().stream().map(CharSequence::toString).collect(Collectors.toSet()));
    }
}
