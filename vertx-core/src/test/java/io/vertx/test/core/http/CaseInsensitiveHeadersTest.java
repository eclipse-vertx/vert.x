package io.vertx.test.core.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;

public class CaseInsensitiveHeadersTest {

  @Test
  public void testCaseInsensitiveHeaders()
      throws Exception {

    CaseInsensitiveHeaders result = new CaseInsensitiveHeaders();

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("", result.toString());
  }

  @Test
  public void testAdd_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    HashMap<String, String> map = new HashMap<String, String>();
    map.put("a", "b");

    MultiMap result = cimap.addAll(map);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("a: b\n",result.toString());
  }

  @Test
  public void testAdd_test2()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    HashMap<String, String> map = new HashMap<String, String>();
    map.put("a", "b");
    map.put("c", "d");

    assertEquals("a: b\nc: d\n",cimap.addAll(map).toString());
  }

  @Test
  public void testAdd_test3()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    HashMap<String, String> map = new HashMap<String, String>();
    map.put("a", "b");

    assertEquals("a: b\n",cimap.addAll(map).toString());
  }

  @Test
  public void testAdd_test4()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    Map<String, String> map = new HashMap<String, String>();

    assertEquals("",cimap.addAll(map).toString());
  }

  @Test
  public void testAdd_test5()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    MultiMap headers = new CaseInsensitiveHeaders();

    assertEquals("",cimap.addAll(headers).toString());
  }

  @Test
  public void testAdd_test7()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    CharSequence name = "name";
    CharSequence value = "value";

    assertEquals("name: value\n",cimap.add(name, value).toString());
  }

  @Test
  public void testAdd_test8()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    CharSequence name = "name";
    ArrayList<CharSequence> values = new ArrayList<CharSequence>();
    values.add("somevalue");

    assertEquals("name: somevalue\n",cimap.add(name, values).toString());
  }

  @Test
  public void testAdd_test9()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "";
    ArrayList<CharSequence> values = new ArrayList<CharSequence>();
    values.add("somevalue");

    assertEquals(": somevalue\n",cimap.add(name, values).toString());
  }

  @Test
  public void testAdd_test10()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "a";
    ArrayList<CharSequence> values = new ArrayList<CharSequence>();
    values.add("somevalue");

    assertEquals("a: somevalue\n",cimap.add(name, values).toString());
  }

  @Test
  public void testAdd_test11()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "";
    String strVal = "";

    assertEquals(": \n",cimap.add(name, strVal).toString());
  }

  @Test
  public void testAdd_test12()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "a";
    String strVal = "b";

    assertEquals("a: b\n",cimap.add(name, strVal).toString());
  }

  @Test
  public void testAdd_test13()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "aaa";
    String strVal = "";

    assertEquals("aaa: \n",cimap.add(name, strVal).toString());
  }

  @Test
  public void testAdd_test14()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "";
    String strVal = "aaa";

    assertEquals(": aaa\n",cimap.add(name, strVal).toString());
  }

  @Test
  public void testAddIterable()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "name";
    List<String> values=new ArrayList<String>();
    values.add("value1");
    values.add("value2");

    MultiMap result = cimap.add(name, values);

    assertEquals(1, result.size());
    assertEquals("name: value1\nname: value2\n",result.toString());
  }

  @Test
  public void testAddMultiMap()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    MultiMap mm=new CaseInsensitiveHeaders();
    mm.add("Header1","value1");
    mm.add("Header2","value2");

    MultiMap result = cimap.addAll(mm);

    assertEquals(2, result.size());
    assertEquals("Header1: value1\nHeader2: value2\n",result.toString());
  }

  @Test
  public void testClear_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    MultiMap result = cimap.clear();

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("",result.toString());
  }

  @Test
  public void testContains_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    CharSequence name = String.valueOf(new Object());

    assertFalse(cimap.contains(name));
  }

  @Test
  public void testContains_test2()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "";

    assertFalse(cimap.contains(name));
  }

  @Test
  public void testContains_test3()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "0123456789";

    boolean result = cimap.contains(name);

    assertFalse(result);
    cimap.add(name,"");
    result = cimap.contains(name);
    assertTrue(result);
  }

  @Test
  public void testEntries_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    List<Map.Entry<String, String>> result = cimap.entries();

    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testGet_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    CharSequence name = String.valueOf(new Object());

    assertNull(cimap.get(name));
  }

  @Test
  public void testGet_test2()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "1";

    assertNull(cimap.get(name));
  }

  @Test
  public void testGet_test3()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "name";

    String result = cimap.get(name);
    assertNull(result);
    cimap.add(name,"value");
    result = cimap.get(name);
    assertEquals("value",result);
  }

  @Test(expected=NullPointerException.class)
  public void testGetNPE() {
    new CaseInsensitiveHeaders().get(null);
  }

  @Test
  public void testGetAll_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    CharSequence name = String.valueOf(new Object());

    List<String> result = cimap.getAll(name);

    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testGetAll_test2()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "1";

    List<String> result = cimap.getAll(name);

    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testGetAll_test3()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "name";

    List<String> result = cimap.getAll(name);

    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testGetAll()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "name";
    cimap.add(name,"value1");
    cimap.add(name,"value2");

    List<String> result = cimap.getAll(name);

    assertNotNull(result);
    assertEquals(2,result.size());
    assertEquals("value1", result.get(0));
  }

  @Test(expected=NullPointerException.class)
  public void testGetAllNPE()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    cimap.getAll(null);
  }

  @Test
  public void testIsEmpty_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    assertTrue(cimap.isEmpty());
  }

  @Test
  public void testIsEmpty_test2()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    cimap.add("a","b");

    assertFalse(cimap.isEmpty());
  }

  @Test
  public void testIterator_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    Iterator<Map.Entry<String, String>> result = cimap.iterator();

    assertNotNull(result);
    assertFalse(result.hasNext());
  }

  @Test
  public void testIterator_test2()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    cimap.add("a", "b");

    Iterator<Map.Entry<String, String>> result = cimap.iterator();

    assertNotNull(result);
    assertTrue(result.hasNext());
  }

  @Test
  public void testNames_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    Set<String> result = cimap.names();

    assertNotNull(result);
    assertEquals(0, result.size());
  }

  @Test
  public void testRemove_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    CharSequence name = String.valueOf(new Object());

    MultiMap result = cimap.remove(name);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
  }

  @Test(expected=NullPointerException.class)
  public void testRemoveNPE()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    cimap.remove(null);
  }

  @Test
  public void testRemove_test2()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "1";

    MultiMap result = cimap.remove(name);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
  }

  @Test
  public void testRemove_test3()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "name";

    MultiMap result = cimap.remove(name);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
  }

  @Test
  public void testRemove_test4()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "name";
    String value= "value";
    cimap.add(name, value);

    assertTrue(cimap.contains(name));

    MultiMap result = cimap.remove(name);

    assertFalse(result.contains(name));
  }

  @Test
  public void testSet_test1()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    HashMap<String, String> headers = new HashMap<String, String>();
    headers.put("", "");

    MultiMap result = cimap.setAll(headers);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals(": \n",result.toString());
  }

  @Test
  public void testSet_test2()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    HashMap<String, String> headers = new HashMap<String, String>();
    headers.put("", "");
    headers.put("aaa", "bbb");

    MultiMap result = cimap.setAll(headers);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals(": \naaa: bbb\n",result.toString());
  }

  @Test
  public void testSet_test3()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    HashMap<String, String> headers = new HashMap<String, String>();
    headers.put("aaa", "bbb");

    MultiMap result = cimap.setAll(headers);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("aaa: bbb\n",result.toString());
  }

  @Test
  public void testSet_test4()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    Map<String, String> headers = new HashMap<String, String>();

    MultiMap result = cimap.setAll(headers);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("",result.toString());
  }

  @Test
  public void testSet_test5()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    MultiMap headers = new CaseInsensitiveHeaders();

    MultiMap result = cimap.setAll(headers);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("",result.toString());
  }

  @Test
  public void testSet_test7()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    CharSequence name = "name";
    CharSequence value = "value";

    MultiMap result = cimap.set(name, value);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("name: value\n",result.toString());
  }

  @Test
  public void testSet_test8()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    CharSequence name = "name";
    ArrayList<CharSequence> values = new ArrayList<CharSequence>();
    values.add("somevalue");

    assertEquals("name: somevalue\n",cimap.set(name, values).toString());
  }

  @Test
  public void testSet_test9()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "";
    ArrayList<CharSequence> values = new ArrayList<CharSequence>();
    values.add("somevalue");

    assertEquals(": somevalue\n",cimap.set(name, values).toString());
  }

  @Test
  public void testSet_test10()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "aaa";
    ArrayList<CharSequence> values = new ArrayList<CharSequence>();
    values.add("somevalue");

    assertEquals("aaa: somevalue\n",cimap.set(name, values).toString());
  }

  @Test
  public void testSet_test11()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "";
    String strVal = "";

    MultiMap result = cimap.set(name, strVal);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals(": \n",result.toString());
  }

  @Test
  public void testSet_test12()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "aaa";
    String strVal = "bbb";

    MultiMap result = cimap.set(name, strVal);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("aaa: bbb\n",result.toString());
  }

  @Test
  public void testSet_test13()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "aaa";
    String strVal = "";

    MultiMap result = cimap.set(name, strVal);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("aaa: \n",result.toString());
  }

  @Test
  public void testSet_test14()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();
    String name = "";
    String strVal = "bbb";

    MultiMap result = cimap.set(name, strVal);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals(": bbb\n",result.toString());
  }

  @Test(expected=NullPointerException.class)
  public void testSetIterableNPE()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    cimap.set("name", (Iterable<String>)null);
  }

  @Test
  public void testSetIterableEmpty()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    String name = "name";
    List<String> values=new ArrayList<String>();

    MultiMap result = cimap.set(name, values);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    assertEquals(0, result.size());
    assertEquals("",result.toString());
  }

  @Test
  public void testSetIterable()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    String name = "name";
    List<String> values=new ArrayList<String>();
    values.add("value1");
    values.add(null);

    MultiMap result = cimap.set(name, values);

    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
    assertEquals("name: value1\n",result.toString());
  }

  @Test
  public void testSize_fixture3()
      throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    assertEquals(0, cimap.size());
    cimap.add("header","value");
    assertEquals(1, cimap.size());
    cimap.add("header2","value2");
    assertEquals(2, cimap.size());
    cimap.add("header","value3");
    assertEquals(2, cimap.size());
  }

  @Test
  public void testGetHashColl() {
    CaseInsensitiveHeaders mm = new CaseInsensitiveHeaders();
    String name1="!~AZ";
    String name2="!~\u0080Y";
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("value1", mm.get(name1));
    assertEquals("value2", mm.get(name2));

    mm = new CaseInsensitiveHeaders();
    name1="";
    name2="\0";
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("value1", mm.get(name1));
    assertEquals("value2", mm.get(name2));

    mm = new CaseInsensitiveHeaders();
    name1="AZa";
    name2="\u0080YA";
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("value1", mm.get(name1));
    assertEquals("value2", mm.get(name2));

    mm = new CaseInsensitiveHeaders();
    name1=" !";
    name2="? ";
    assertTrue("hash error",hash(name1)==hash(name2));
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("value1", mm.get(name1));
    assertEquals("value2", mm.get(name2));

    mm = new CaseInsensitiveHeaders();
    name1="\u0080a";
    name2="Ab";
    assertTrue("hash error",hash(name1)==hash(name2));
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("value1", mm.get(name1));
    assertEquals("value2", mm.get(name2));

    // same bucket, different hash
    mm = new CaseInsensitiveHeaders();
    name1="A";
    name2="R";
    assertTrue("hash error",index(hash(name1))==index(hash(name2)));
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("value1", mm.get(name1));
    assertEquals("value2", mm.get(name2));
  }

  @Test
  public void testGetAllHashColl() {
    CaseInsensitiveHeaders mm = new CaseInsensitiveHeaders();
    String name1="AZ";
    String name2="\u0080Y";
    assertTrue("hash error",hash(name1)==hash(name2));
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("[value1]", mm.getAll(name1).toString());
    assertEquals("[value2]", mm.getAll(name2).toString());

    mm = new CaseInsensitiveHeaders();
    name1="A";
    name2="R";
    assertTrue("hash error",index(hash(name1))==index(hash(name2)));
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    assertEquals(2, mm.size());
    assertEquals("[value1]", mm.getAll(name1).toString());
    assertEquals("[value2]", mm.getAll(name2).toString());
  }

  @Test
  public void testRemoveHashColl() {
    CaseInsensitiveHeaders mm = new CaseInsensitiveHeaders();
    String name1="AZ";
    String name2="\u0080Y";
    String name3="RZ";
    assertTrue("hash error",hash(name1)==hash(name2));
    mm.add(name1, "value1");
    mm.add(name2, "value2");
    mm.add(name3, "value3");
    mm.add(name1, "value4");
    mm.add(name2, "value5");
    mm.add(name3, "value6");
    assertEquals(3, mm.size());
    mm.remove(name1);
    mm.remove(name2);
    assertEquals(1, mm.size());

    mm = new CaseInsensitiveHeaders();
    name1="A";
    name2="R";
    assertTrue("hash error",index(hash(name1))==index(hash(name2)));
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
    for (int i = name.length() - 1; i >= 0; i --) {
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
    CaseInsensitiveHeaders mm = new CaseInsensitiveHeaders();
    String name1="";
    long value=Integer.MAX_VALUE;
    value++;
    int base=31;
    long pow=1;

    while(value>pow*base) {
      pow*=base;
    }

    while(pow!=0) {
      long mul=value/pow;
      name1=((char)mul)+name1;
      value-=pow*mul;
      pow/=base;
    }
    name1=((char)value)+name1;
    mm.add(name1,"value");
    assertEquals("value",mm.get(name1));
  }

  // we have to sort the string since a map doesn't do sorting
  private String sortByLine(String str) {
    String lines[]=str.split("\n");
    Arrays.sort(lines);
    StringBuilder sb=new StringBuilder();
    for(String s:lines) {
      sb.append(s);
      sb.append("\n");
    }
    return sb.toString();
  }

  @Test
  public void testToString() {
    MultiMap mm=new CaseInsensitiveHeaders();
    assertEquals("",mm.toString());
    mm.add("Header1","Value1");
    assertEquals("Header1: Value1\n",
        sortByLine(mm.toString()));
    mm.add("Header2","Value2");
    assertEquals("Header1: Value1\n" +
        "Header2: Value2\n",
        sortByLine(mm.toString()));
    mm.add("Header1","Value3");
    assertEquals("Header1: Value1\n" + 
        "Header1: Value3\n" + 
        "Header2: Value2\n",
        sortByLine(mm.toString()));
    mm.remove("Header1");
    assertEquals("Header2: Value2\n",
        sortByLine(mm.toString()));
    mm.set("Header2","Value4");
    assertEquals("Header2: Value4\n",
        sortByLine(mm.toString()));
  }

  /*
   * unit tests for public method in MapEntry
   * (isn't actually used in the implementation)
   */

  @Test
  public void testMapEntrySetValue() throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    cimap.add("Header","oldvalue");

    for(Map.Entry<String, String> me:cimap) {
      me.setValue("newvalue");
    }
    assertEquals("newvalue",cimap.get("Header"));
  }

  @Test
  public void testMapEntryToString() throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    cimap.add("Header","value");

    assertEquals("Header=value", cimap.iterator().next().toString());
  }

  @Test(expected=NullPointerException.class)
  public void testMapEntrySetValueNull() throws Exception {
    CaseInsensitiveHeaders cimap = new CaseInsensitiveHeaders();

    cimap.add("Header","oldvalue");

    for(Map.Entry<String, String> me:cimap) {
      me.setValue(null);
    }
  }

}
