package org.vertx.java.core.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonArray implements Iterable<Object> {

  List list = new ArrayList();

  public JsonArray(List array) {
    this.list = array;
  }

  public JsonArray(Object[] array) {
    this.list = Arrays.asList(array);
  }

  public JsonArray() {
  }

  public JsonArray(String jsonString) {
    try {
      list = Json.mapper.readValue(jsonString, List.class);
      } catch (Exception e) {
      throw new DecodeException("Failed to decode JSON array from string: " + jsonString);
    }
  }

  public JsonArray add(Object obj) {
    if (obj instanceof JsonObject) {
      obj = ((JsonObject)obj).map;
    } else if (obj instanceof JsonArray) {
      obj = ((JsonArray)obj).list;
    }
    list.add(obj);
    return this;
  }

  public int size() {
    return list.size();
  }

  public Iterator iterator() {
    return new Iterator() {

      Iterator iter = list.iterator();

      @Override
      public boolean hasNext() {
        return iter.hasNext();
      }

      @Override
      public Object next() {
        Object next = iter.next();
        if (next != null) {
          if (next instanceof List) {
            next = new JsonArray((List)next);
          } else if (next instanceof Map) {
            next = new JsonObject((Map)next);
          }
        }
        return next;
      }

      @Override
      public void remove() {
      }
    };
  }

  public String encode() throws EncodeException {
    return Json.encode(this.list);
  }
}
