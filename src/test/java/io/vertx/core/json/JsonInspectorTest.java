package io.vertx.core.json;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static io.vertx.core.json.impl.JsonUtil.inspect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsonInspectorTest {

  @Test
  public void testColors() {
    JsonObject o = new JsonObject("{\n" +
      "  \"colors\": [\n" +
      "    {\n" +
      "      \"color\": \"black\",\n" +
      "      \"category\": \"hue\",\n" +
      "      \"type\": \"primary\",\n" +
      "      \"code\": {\n" +
      "        \"rgba\": [255,255,255,1],\n" +
      "        \"hex\": \"#000\"\n" +
      "      }\n" +
      "    },\n" +
      "    {\n" +
      "      \"color\": \"white\",\n" +
      "      \"category\": \"value\",\n" +
      "      \"code\": {\n" +
      "        \"rgba\": [0,0,0,1],\n" +
      "        \"hex\": \"#FFF\"\n" +
      "      }\n" +
      "    },\n" +
      "    {\n" +
      "      \"color\": \"red\",\n" +
      "      \"category\": \"hue\",\n" +
      "      \"type\": \"primary\",\n" +
      "      \"code\": {\n" +
      "        \"rgba\": [255,0,0,1],\n" +
      "        \"hex\": \"#FF0\"\n" +
      "      }\n" +
      "    },\n" +
      "    {\n" +
      "      \"color\": \"blue\",\n" +
      "      \"category\": \"hue\",\n" +
      "      \"type\": \"primary\",\n" +
      "      \"code\": {\n" +
      "        \"rgba\": [0,0,255,1],\n" +
      "        \"hex\": \"#00F\"\n" +
      "      }\n" +
      "    },\n" +
      "    {\n" +
      "      \"color\": \"yellow\",\n" +
      "      \"category\": \"hue\",\n" +
      "      \"type\": \"primary\",\n" +
      "      \"code\": {\n" +
      "        \"rgba\": [255,255,0,1],\n" +
      "        \"hex\": \"#FF0\"\n" +
      "      }\n" +
      "    },\n" +
      "    {\n" +
      "      \"color\": \"green\",\n" +
      "      \"category\": \"hue\",\n" +
      "      \"type\": \"secondary\",\n" +
      "      \"code\": {\n" +
      "        \"rgba\": [0,255,0,1],\n" +
      "        \"hex\": \"#0F0\"\n" +
      "      }\n" +
      "    }\n" +
      "  ]\n" +
      "}");

    // collapase large objects
    assertEquals(
      "{colors: [{color: \"black\", category: \"hue\", type: \"primary\", code: {...}}, {color: \"white\", category: \"value\", code: {...}}, {color: \"red\", category: \"hue\", type: \"primary\", code: {...}}, {color: \"blue\", category: \"hue\", type: \"primary\", code: {...}}, {color: \"yellow\", category: \"hue\", type: \"primary\", code: {...}}, {color: \"green\", category: \"hue\", type: \"secondary\", code: {...}}]}",
      inspect(o));
  }

  @Test
  public void testCircularContainer() {
    List list = new ArrayList();
    JsonObject o = new JsonObject();
    JsonArray a = new JsonArray(list);

    a.add(1);
    list.add(list);
    o.put("a", a);
    o.put("o", o);
    o.put("s", "s");
    o.put("n", 1);
    o.put("b", true);
    o.put("null", null);

    // a wrapper should be detected as a circular reference
    int ref1 = System.identityHashCode(a.getList());
    int ref2 = System.identityHashCode(o.getMap());

    assertEquals(
      String.format("{a: [1, [Circular *%d]], o: {a: [1, [...]], o: {Circular *%d}, s: \"s\", n: 1, b: true, null: null}, s: \"s\", n: 1, b: true, null: null}", ref1, ref2),
      inspect(o));
  }

  @Test
  public void testCircularWrapper() {
    JsonObject o = new JsonObject();
    JsonArray a = new JsonArray();

    a.add(1);
    a.add(new JsonArray(a.getList()));
    o.put("a", a);
    o.put("o", o);
    o.put("s", "s");
    o.put("n", 1);
    o.put("b", true);
    o.put("null", null);

    // a wrapper around the same containers should be detected as a circular reference
    int ref1 = System.identityHashCode(a.getList());
    int ref2 = System.identityHashCode(o.getMap());

    assertEquals(
      String.format("{a: [1, [Circular *%d]], o: {a: [1, [...]], o: {Circular *%d}, s: \"s\", n: 1, b: true, null: null}, s: \"s\", n: 1, b: true, null: null}", ref1, ref2),
      inspect(o));
  }

  @Test
  public void testDuplicatesAreOk()  {
    JsonObject o = new JsonObject();
    JsonArray a = new JsonArray();
    a.add(1);
    a.add(2);

    o.put("a0", a);
    o.put("a1", a);

    // verify that the circular reference is not mistaken for a duplicate references
    assertEquals(
      "{a0: [1, 2], a1: [1, 2]}",
      inspect(o));
  }

  @Test
  public void testDeepJson() {
    JsonObject o = new JsonObject("{\n" +
      "    \"created\": \"2020-05-12T15:10:37Z\",\n" +
      "    \"device\": {\n" +
      "        \"device_info\": {\n" +
      "            \"device_fw\": 204,\n" +
      "            \"device_sn\": \"06-02133\",\n" +
      "            \"device_trait\": 2,\n" +
      "            \"device_type\": 190\n" +
      "        },\n" +
      "        \"timeseries\": [\n" +
      "            {\n" +
      "                \"configuration\": {\n" +
      "                    \"sensors\": [\n" +
      "                        {\n" +
      "                            \"measurements\": [\n" +
      "                                \"BATTERY\",\n" +
      "                                \"BATTERY_MV\"\n" +
      "                            ],\n" +
      "                            \"port\": 7,\n" +
      "                            \"sensor_bonus_value\": \"Unavailable\",\n" +
      "                            \"sensor_firmware_ver\": \"Unavailable\",\n" +
      "                            \"sensor_number\": 133,\n" +
      "                            \"sensor_sn\": \"Unavailable\"\n" +
      "                        },\n" +
      "                        {\n" +
      "                            \"measurements\": [\n" +
      "                                \"REFERENCE_KPA\",\n" +
      "                                \"TEMPC_LOGGER\"\n" +
      "                            ],\n" +
      "                            \"port\": 8,\n" +
      "                            \"sensor_bonus_value\": \"Unavailable\",\n" +
      "                            \"sensor_firmware_ver\": \"Unavailable\",\n" +
      "                            \"sensor_number\": 134,\n" +
      "                            \"sensor_sn\": \"Unavailable\"\n" +
      "                        }\n" +
      "                    ],\n" +
      "                    \"valid_since\": \"2018-08-11T21:45:00Z\",\n" +
      "                    \"values\": [\n" +
      "                        [\n" +
      "                            1534023900,\n" +
      "                            0,\n" +
      "                            19,\n" +
      "                            [\n" +
      "                                {\n" +
      "                                    \"description\": \"Battery Percent\",\n" +
      "                                    \"error\": false,\n" +
      "                                    \"units\": \"%\",\n" +
      "                                    \"value\": 100\n" +
      "                                },\n" +
      "                                {\n" +
      "                                    \"description\": \"Battery Voltage\",\n" +
      "                                    \"error\": false,\n" +
      "                                    \"units\": \" mV\",\n" +
      "                                    \"value\": 7864\n" +
      "                                }\n" +
      "                            ],\n" +
      "                            [\n" +
      "                                {\n" +
      "                                    \"description\": \"Reference Pressure\",\n" +
      "                                    \"error\": false,\n" +
      "                                    \"units\": \" kPa\",\n" +
      "                                    \"value\": 100.62\n" +
      "                                },\n" +
      "                                {\n" +
      "                                    \"description\": \"Logger Temperature\",\n" +
      "                                    \"error\": false,\n" +
      "                                    \"units\": \" \\u00b0C\",\n" +
      "                                    \"value\": 28.34\n" +
      "                                }\n" +
      "                            ]\n" +
      "                        ]\n" +
      "                            ]\n" +
      "                    }\n" +
      "                }]\n" +
      "            }\n" +
      "}     ");

    // inspect will collapse the timeseries array as it contains multiple levels of nested objects
    assertEquals(
      "{created: \"2020-05-12T15:10:37Z\", device: {device_info: {device_fw: 204, device_sn: \"06-02133\", device_trait: 2, device_type: 190}, timeseries: [{...}]}}",
      inspect(o));
  }

  @Test
  public void testDoubleCircularRef() {
  JsonObject obj = new JsonObject();
    obj.put("a", new JsonArray().add(obj));
    obj.put("b", new JsonObject());
    obj.getJsonObject("b").put("inner", obj.getValue("b"));
    obj.getJsonObject("b").put("obj", obj);

    // references are to the internal map as vert.x json types are wrappers, multiple wrapper can refer to the same
    // container instance
    int ref1 = System.identityHashCode(obj.getMap());
    int ref2 = System.identityHashCode(obj.getJsonObject("b").getMap());

    assertEquals(
      String.format("{a: [{Circular *%d}], b: {inner: {Circular *%d}, obj: {Circular *%d}}}", ref1, ref2, ref1),
      inspect(obj));
  }

  @Test
  public void testLargeArrays() {
    JsonArray arr = new JsonArray();
    for (int i = 0; i < 200; i++) {
      arr.add(i);
    }
    assertEquals(
      "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, ...]",
      inspect(arr));
  }

  @Test
  public void testLargeObjects() {
    JsonObject obj = new JsonObject();
    for (int i = 0; i < 200; i++) {
      obj.put(Integer.toString(i), i);
    }
    assertEquals(
      "{0: 0, 1: 1, 2: 2, 3: 3, 4: 4, 5: 5, 6: 6, 7: 7, 8: 8, 9: 9, 10: 10, 11: 11, 12: 12, 13: 13, 14: 14, 15: 15, 16: 16, 17: 17, 18: 18, 19: 19, 20: 20, 21: 21, 22: 22, 23: 23, 24: 24, 25: 25, 26: 26, 27: 27, 28: 28, 29: 29, 30: 30, 31: 31, 32: 32, 33: 33, 34: 34, 35: 35, 36: 36, 37: 37, 38: 38, 39: 39, 40: 40, 41: 41, 42: 42, 43: 43, 44: 44, 45: 45, 46: 46, 47: 47, 48: 48, 49: 49, 50: 50, 51: 51, 52: 52, 53: 53, 54: 54, 55: 55, 56: 56, 57: 57, 58: 58, 59: 59, 60: 60, 61: 61, 62: 62, 63: 63, 64: 64, 65: 65, 66: 66, 67: 67, 68: 68, 69: 69, 70: 70, 71: 71, 72: 72, 73: 73, 74: 74, 75: 75, 76: 76, 77: 77, 78: 78, 79: 79, 80: 80, 81: 81, 82: 82, 83: 83, 84: 84, 85: 85, 86: 86, 87: 87, 88: 88, 89: 89, 90: 90, 91: 91, 92: 92, 93: 93, 94: 94, 95: 95, 96: 96, 97: 97, 98: 98, 99: 99, ...}",
      inspect(obj));
  }


  @Test
  public void testLargeStrings() {
    String loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Pellentesque habitant morbi tristique senectus et. Mi in nulla posuere sollicitudin aliquam. Adipiscing enim eu turpis egestas pretium aenean pharetra magna ac. Duis convallis convallis tellus id interdum velit laoreet id. Consectetur libero id faucibus nisl. Dui faucibus in ornare quam viverra. Gravida quis blandit turpis cursus in hac habitasse. Ac tortor vitae purus faucibus ornare suspendisse. Nec sagittis aliquam malesuada bibendum arcu vitae elementum curabitur vitae. In nulla posuere sollicitudin aliquam ultrices sagittis orci. Sed odio morbi quis commodo odio aenean sed. Risus nullam eget felis eget nunc lobortis mattis aliquam faucibus. ";

    for (int i = 0; i < 10; i++) {
      loremIpsum += loremIpsum;
    }

    JsonObject obj = new JsonObject().put("msg", loremIpsum);

    assertEquals(
      12 // {msg: "..."} (12 is the number of extra characters in the format)
        + 10000,  // the inspection string length limit
      inspect(obj).length());
  }
}
