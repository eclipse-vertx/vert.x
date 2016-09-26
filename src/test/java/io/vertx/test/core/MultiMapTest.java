package io.vertx.test.core;

import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

public abstract class MultiMapTest {
    
    protected MultiMap mmap;

    protected abstract MultiMap createMap();


    @Test
    public void testCaseInsensitiveHeaders()
            throws Exception {
        assertNotNull(mmap);
        assertTrue(mmap.isEmpty());
        assertEquals(0, mmap.size());
        assertEquals("", mmap.toString());
    }

    @Test
    public void testAddTest1()
            throws Exception {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("a", "b");

        MultiMap result = mmap.addAll(map);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("a: b\n", result.toString());
    }

    @Test
    public void testAddTest2()
            throws Exception {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("a", "b");
        map.put("c", "d");

        assertEquals("a: b\nc: d\n", mmap.addAll(map).toString());
    }

    @Test
    public void testAddTest3()
            throws Exception {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("a", "b");

        assertEquals("a: b\n", mmap.addAll(map).toString());
    }

    @Test
    public void testAddTest4()
            throws Exception {
        Map<String, String> map = new HashMap<String, String>();

        assertEquals("", mmap.addAll(map).toString());
    }

    @Test
    public void testAddTest5()
            throws Exception {
        MultiMap mmap = this.mmap;
        MultiMap headers = createMap();

        assertEquals("", mmap.addAll(headers).toString());
    }

    @Test
    public void testAddTest7()
            throws Exception {
        CharSequence name = "name";
        CharSequence value = "value";

        assertEquals("name: value\n", mmap.add(name, value).toString());
    }

    @Test
    public void testAddTest8()
            throws Exception {
        CharSequence name = "name";
        ArrayList<CharSequence> values = new ArrayList<CharSequence>();
        values.add("somevalue");

        assertEquals("name: somevalue\n", mmap.add(name, values).toString());
    }

    @Test
    public void testAddTest9()
            throws Exception {
        String name = "";
        ArrayList<CharSequence> values = new ArrayList<CharSequence>();
        values.add("somevalue");

        assertEquals(": somevalue\n", mmap.add(name, values).toString());
    }

    @Test
    public void testAddTest10()
            throws Exception {
        String name = "a";
        ArrayList<CharSequence> values = new ArrayList<CharSequence>();
        values.add("somevalue");

        assertEquals("a: somevalue\n", mmap.add(name, values).toString());
    }

    @Test
    public void testAddTest11()
            throws Exception {
        String name = "";
        String strVal = "";

        assertEquals(": \n", mmap.add(name, strVal).toString());
    }

    @Test
    public void testAddTest12()
            throws Exception {
        String name = "a";
        String strVal = "b";

        assertEquals("a: b\n", mmap.add(name, strVal).toString());
    }

    @Test
    public void testAddTest13()
            throws Exception {
        String name = "aaa";
        String strVal = "";

        assertEquals("aaa: \n", mmap.add(name, strVal).toString());
    }

    @Test
    public void testAddTest14()
            throws Exception {
        String name = "";
        String strVal = "aaa";

        assertEquals(": aaa\n", mmap.add(name, strVal).toString());
    }

    @Test
    public void testAddIterable()
            throws Exception {
        String name = "name";
        List<String> values = new ArrayList<String>();
        values.add("value1");
        values.add("value2");

        MultiMap result = mmap.add(name, values);

        assertEquals(1, result.size());
        assertEquals("name: value1\nname: value2\n", result.toString());
    }

    @Test
    public void testAddMultiMap()
            throws Exception {

        MultiMap mm = createMap();
        mm.add("Header1", "value1");
        mm.add("Header2", "value2");

        MultiMap result = mmap.addAll(mm);

        assertEquals(2, result.size());
        assertEquals("Header1: value1\nHeader2: value2\n", sortByLine(result.toString()));
    }

    @Test
    public void testClearTest1()
            throws Exception {

        MultiMap result = mmap.clear();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertEquals("", result.toString());
    }

    @Test
    public void testContainsTest1()
            throws Exception {
        CharSequence name = String.valueOf(new Object());

        assertFalse(mmap.contains(name));
    }

    @Test
    public void testContainsTest2()
            throws Exception {
        String name = "";

        assertFalse(mmap.contains(name));
    }

    @Test
    public void testContainsTest3()
            throws Exception {
        String name = "0123456789";

        boolean result = mmap.contains(name);

        assertFalse(result);
        mmap.add(name, "");
        result = mmap.contains(name);
        assertTrue(result);
    }

    @Test
    public void testEntries() {
        mmap.set("foo", Arrays.<String>asList("foo_value_1", "foo_value_2"));
        List<Map.Entry<String, String>> entries = mmap.entries();
        assertEquals(entries.size(), 2);
        assertEquals("foo", entries.get(0).getKey());
        assertEquals("foo_value_1", entries.get(0).getValue());
        mmap.set("bar", "bar_value");
        List<Map.Entry<String, String>> entries2 = mmap.entries();
        assertEquals("bar_value", entries2
                        .stream()
                        .filter(entry -> "bar".equals(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .get());
    }

    @Test
    public void testGetTest1()
            throws Exception {
        CharSequence name = String.valueOf(new Object());

        assertNull(mmap.get(name));
    }

    @Test
    public void testGetTest2()
            throws Exception {
        String name = "1";

        assertNull(mmap.get(name));
    }

    @Test
    public void testGetTest3()
            throws Exception {
        String name = "name";

        String result = mmap.get(name);
        assertNull(result);
        mmap.add(name, "value");
        result = mmap.get(name);
        assertEquals("value", result);
    }

    @Test(expected = NullPointerException.class)
    public void testGetNPE() {
        new CaseInsensitiveHeaders().get(null);
    }

    @Test
    public void testGetAllTest1()
            throws Exception {
        CharSequence name = String.valueOf(new Object());

        List<String> result = mmap.getAll(name);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testGetAllTest2()
            throws Exception {
        String name = "1";

        List<String> result = mmap.getAll(name);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testGetAllTest3()
            throws Exception {
        String name = "name";

        List<String> result = mmap.getAll(name);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testGetAll()
            throws Exception {
        String name = "name";
        mmap.add(name, "value1");
        mmap.add(name, "value2");

        List<String> result = mmap.getAll(name);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("value1", result.get(0));
    }

    @Test(expected = NullPointerException.class)
    public void testGetAllNPE()
            throws Exception {
        mmap.getAll(null);
    }

    @Test
    public void testIsEmptyTest1()
            throws Exception {

        assertTrue(mmap.isEmpty());
    }

    @Test
    public void testIsEmptyTest2()
            throws Exception {
        mmap.add("a", "b");

        assertFalse(mmap.isEmpty());
    }

    @Test
    public void testIteratorTest1()
            throws Exception {

        Iterator<Map.Entry<String, String>> result = mmap.iterator();

        assertNotNull(result);
        assertFalse(result.hasNext());
    }

    @Test
    public void testIteratorTest2()
            throws Exception {
        mmap.add("a", "b");

        Iterator<Map.Entry<String, String>> result = mmap.iterator();

        assertNotNull(result);
        assertTrue(result.hasNext());
    }

    @Test
    public void testNamesTest1()
            throws Exception {

        Set<String> result = mmap.names();

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testRemoveTest1()
            throws Exception {
        CharSequence name = String.valueOf(new Object());

        MultiMap result = mmap.remove(name);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
    }

    @Test(expected = NullPointerException.class)
    public void testRemoveNPE()
            throws Exception {
        mmap.remove(null);
    }

    @Test
    public void testRemoveTest2()
            throws Exception {
        String name = "1";

        MultiMap result = mmap.remove(name);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
    }

    @Test
    public void testRemoveTest3()
            throws Exception {
        String name = "name";

        MultiMap result = mmap.remove(name);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
    }

    @Test
    public void testRemoveTest4()
            throws Exception {
        String name = "name";
        String value = "value";
        mmap.add(name, value);

        assertTrue(mmap.contains(name));

        MultiMap result = mmap.remove(name);

        assertFalse(result.contains(name));
    }

    @Test
    public void testSetTest1()
            throws Exception {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("", "");

        MultiMap result = mmap.setAll(headers);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(": \n", result.toString());
    }

    @Test
    public void testSetTest2()
            throws Exception {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("", "");
        headers.put("aaa", "bbb");

        MultiMap result = mmap.setAll(headers);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        assertEquals(": \naaa: bbb\n", result.toString());
    }

    @Test
    public void testSetTest3()
            throws Exception {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("aaa", "bbb");

        MultiMap result = mmap.setAll(headers);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("aaa: bbb\n", result.toString());
    }

    @Test
    public void testSetTest4()
            throws Exception {
        Map<String, String> headers = new HashMap<String, String>();

        MultiMap result = mmap.setAll(headers);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertEquals("", result.toString());
    }

    @Test
    public void testSetTest5()
            throws Exception {
        MultiMap headers = createMap();

        MultiMap result = mmap.setAll(headers);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertEquals("", result.toString());
    }

    @Test
    public void testSetTest7()
            throws Exception {
        CharSequence name = "name";
        CharSequence value = "value";

        MultiMap result = mmap.set(name, value);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("name: value\n", result.toString());
    }

    @Test
    public void testSetTest8()
            throws Exception {
        CharSequence name = "name";
        ArrayList<CharSequence> values = new ArrayList<CharSequence>();
        values.add("somevalue");

        assertEquals("name: somevalue\n", mmap.set(name, values).toString());
    }

    @Test
    public void testSetTest9()
            throws Exception {
        String name = "";
        ArrayList<CharSequence> values = new ArrayList<CharSequence>();
        values.add("somevalue");

        assertEquals(": somevalue\n", mmap.set(name, values).toString());
    }

    @Test
    public void testSetTest10()
            throws Exception {
        String name = "aaa";
        ArrayList<CharSequence> values = new ArrayList<CharSequence>();
        values.add("somevalue");

        assertEquals("aaa: somevalue\n", mmap.set(name, values).toString());
    }

    @Test
    public void testSetTest11()
            throws Exception {
        String name = "";
        String strVal = "";

        MultiMap result = mmap.set(name, strVal);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(": \n", result.toString());
    }

    @Test
    public void testSetTest12()
            throws Exception {
        String name = "aaa";
        String strVal = "bbb";

        MultiMap result = mmap.set(name, strVal);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("aaa: bbb\n", result.toString());
    }

    @Test
    public void testSetTest13()
            throws Exception {
        String name = "aaa";
        String strVal = "";

        MultiMap result = mmap.set(name, strVal);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("aaa: \n", result.toString());
    }

    @Test
    public void testSetTest14()
            throws Exception {
        String name = "";
        String strVal = "bbb";

        MultiMap result = mmap.set(name, strVal);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(": bbb\n", result.toString());
    }

    @Test(expected = NullPointerException.class)
    public void testSetIterableNPE()
            throws Exception {
        mmap.set("name", (Iterable<String>) null);
    }

    @Test
    public void testSetIterableEmpty()
            throws Exception {

        String name = "name";
        List<String> values = new ArrayList<String>();

        MultiMap result = mmap.set(name, values);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.size());
        assertEquals("", result.toString());
    }

    @Test
    public void testSetIterable()
            throws Exception {

        String name = "name";
        List<String> values = new ArrayList<String>();
        values.add("value1");
        values.add(null);

        MultiMap result = mmap.set(name, values);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("name: value1\n", result.toString());
    }

    @Test
    public void testSize()
            throws Exception {

        assertEquals(0, mmap.size());
        mmap.add("header", "value");
        assertEquals(1, mmap.size());
        mmap.add("header2", "value2");
        assertEquals(2, mmap.size());
        mmap.add("header", "value3");
        assertEquals(2, mmap.size());
        mmap.add("header3", Arrays.<String>asList("value", "value2", "value3"));
        assertEquals(3, mmap.size());
    }

    @Test
    public void testGetHashColl() {
        MultiMap mm;
        String name1 = "!~AZ";
        String name2 = "!~\u0080Y";
        mmap.add(name1, "value1");
        mmap.add(name2, "value2");
        assertEquals(2, mmap.size());
        assertEquals("value1", mmap.get(name1));
        assertEquals("value2", mmap.get(name2));

        mm = createMap();
        name1 = "";
        name2 = "\0";
        mm.add(name1, "value1");
        mm.add(name2, "value2");
        assertEquals(2, mm.size());
        assertEquals("value1", mm.get(name1));
        assertEquals("value2", mm.get(name2));

        mm = createMap();
        name1 = "AZa";
        name2 = "\u0080YA";
        mm.add(name1, "value1");
        mm.add(name2, "value2");
        assertEquals(2, mm.size());
        assertEquals("value1", mm.get(name1));
        assertEquals("value2", mm.get(name2));

        mm = createMap();
        name1 = " !";
        name2 = "? ";
        assertTrue("hash error", hash(name1) == hash(name2));
        mm.add(name1, "value1");
        mm.add(name2, "value2");
        assertEquals(2, mm.size());
        assertEquals("value1", mm.get(name1));
        assertEquals("value2", mm.get(name2));

        mm = createMap();
        name1 = "\u0080a";
        name2 = "Ab";
        assertTrue("hash error", hash(name1) == hash(name2));
        mm.add(name1, "value1");
        mm.add(name2, "value2");
        assertEquals(2, mm.size());
        assertEquals("value1", mm.get(name1));
        assertEquals("value2", mm.get(name2));

        // same bucket, different hash
        mm = createMap();
        name1 = "A";
        name2 = "R";
        assertTrue("hash error", index(hash(name1)) == index(hash(name2)));
        mm.add(name1, "value1");
        mm.add(name2, "value2");
        assertEquals(2, mm.size());
        assertEquals("value1", mm.get(name1));
        assertEquals("value2", mm.get(name2));
    }

    @Test
    public void testGetAllHashColl() {
        MultiMap mm;
        String name1 = "AZ";
        String name2 = "\u0080Y";
        assertTrue("hash error", hash(name1) == hash(name2));
        mmap.add(name1, "value1");
        mmap.add(name2, "value2");
        assertEquals(2, mmap.size());
        assertEquals("[value1]", mmap.getAll(name1).toString());
        assertEquals("[value2]", mmap.getAll(name2).toString());

        mm = createMap();
        name1 = "A";
        name2 = "R";
        assertTrue("hash error", index(hash(name1)) == index(hash(name2)));
        mm.add(name1, "value1");
        mm.add(name2, "value2");
        assertEquals(2, mm.size());
        assertEquals("[value1]", mm.getAll(name1).toString());
        assertEquals("[value2]", mm.getAll(name2).toString());
    }

    @Test
    public void testRemoveHashColl() {
        MultiMap mm;
        String name1 = "AZ";
        String name2 = "\u0080Y";
        String name3 = "RZ";
        assertTrue("hash error", hash(name1) == hash(name2));
        mmap.add(name1, "value1");
        mmap.add(name2, "value2");
        mmap.add(name3, "value3");
        mmap.add(name1, "value4");
        mmap.add(name2, "value5");
        mmap.add(name3, "value6");
        assertEquals(3, mmap.size());
        mmap.remove(name1);
        mmap.remove(name2);
        assertEquals(1, mmap.size());

        mm = createMap();
        name1 = "A";
        name2 = "R";
        assertTrue("hash error", index(hash(name1)) == index(hash(name2)));
        mm.add(name1, "value1");
        mm.add(name2, "value2");
        assertEquals(2, mm.size());
        mm.remove(name1);
        mm.remove(name2);
        assertTrue("not empty", mm.isEmpty());
    }

    // hash function copied from method under test
    private static int hash(String name) {
        int h = 0;
        for (int i = name.length() - 1; i >= 0; i--) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c += 32;
            }
            h = 31 * h + c;
        }

        if (h > 0) {
            return h;
        } else if (h == Integer.MIN_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return -h;
        }
    }

    private static int index(int hash) {
        return hash % 17;
    }

    // construct a string with hash==MIN_VALUE
    // to get coverage of the if in hash()
    // we will calculate the representation of
    // MAX_VALUE+1 in base31, which wraps around to
    // MIN_VALUE in int representation
    @Test
    public void testHashMININT() {
        String name1 = "";
        long value = Integer.MAX_VALUE;
        value++;
        int base = 31;
        long pow = 1;

        while (value > pow * base) {
            pow *= base;
        }

        while (pow != 0) {
            long mul = value / pow;
            name1 = ((char) mul) + name1;
            value -= pow * mul;
            pow /= base;
        }
        name1 = ((char) value) + name1;
        mmap.add(name1, "value");
        assertEquals("value", mmap.get(name1));
    }

    // we have to sort the string since a map doesn't do sorting
    protected String sortByLine(String str) {
        String[] lines = str.split("\n");
        Arrays.sort(lines);
        StringBuilder sb = new StringBuilder();
        for (String s:lines) {
            sb.append(s);
            sb.append("\n");
        }
        return sb.toString();
    }

    @Test
    public void testToString() {
        assertEquals("", mmap.toString());
        mmap.add("Header1", "Value1");
        assertEquals("Header1: Value1\n",
                sortByLine(mmap.toString()));
        mmap.add("Header2", "Value2");
        assertEquals("Header1: Value1\n"
                        + "Header2: Value2\n",
                sortByLine(mmap.toString()));
        mmap.add("Header1", "Value3");
        assertEquals("Header1: Value1\n"
                        + "Header1: Value3\n"
                        + "Header2: Value2\n",
                sortByLine(mmap.toString()));
        mmap.remove("Header1");
        assertEquals("Header2: Value2\n",
                sortByLine(mmap.toString()));
        mmap.set("Header2", "Value4");
        assertEquals("Header2: Value4\n",
                sortByLine(mmap.toString()));
    }

  /*
   * unit tests for public method in MapEntry
   * (isn't actually used in the implementation)
   */

    @Test
    public void testMapEntrySetValue() throws Exception {

        mmap.add("Header", "oldvalue");

        for (Map.Entry<String, String> me: mmap) {
            me.setValue("newvalue");
        }
        assertEquals("newvalue", mmap.get("Header"));
    }

    @Test
    public void testMapEntryToString() throws Exception {

        mmap.add("Header", "value");

        assertEquals("Header: value", mmap.iterator().next().toString());
    }

    @Test(expected = NullPointerException.class)
    public void testMapEntrySetValueNull() throws Exception {

        mmap.add("Header", "oldvalue");

        for (Map.Entry<String, String> me: mmap) {
            me.setValue(null);
        }
    }

    @Test
    public void testMultipleValues() {
        List<String> ts = Arrays.asList("1", "2", "3");
        mmap.set("foo", ts);
        assertEquals(1, mmap.size());
        mmap.set("bar", "1");
        assertEquals(2, mmap.size());
    }
}
