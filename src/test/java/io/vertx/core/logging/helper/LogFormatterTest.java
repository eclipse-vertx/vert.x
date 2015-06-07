package io.vertx.core.logging.helper;

import io.vertx.core.logging.LogFormatter;
import io.vertx.core.logging.LogFormatter.LogTuple;

import org.junit.Assert;
import org.junit.Test;

public class LogFormatterTest {
  @Test
  public void testParamsFormatOneParamNotThrowable() throws Exception {
    LogTuple tupple = LogFormatter.format("the message pattern {} ...", "param1");
    Assert.assertEquals("the message pattern param1 ...", tupple.getMessage());
    Assert.assertNull("No throwable expected", tupple.getThrowable());
  }

  @Test
  public void testParamsFormatOneParamThrowable() throws Exception {
    Throwable throwable = new Throwable();
    LogTuple tupple = LogFormatter.format("the message pattern {} ...", throwable);
    Assert.assertEquals("the message pattern {} ...", tupple.getMessage());
    Assert.assertEquals("Throwable expected", throwable, tupple.getThrowable());
  }

  @Test
  public void testParamsFormatTwoParamsNoThrowable() throws Exception {
    LogTuple tupple = LogFormatter.format("the message pattern {} {} ...", "param1", "param2");
    Assert.assertEquals("the message pattern param1 param2 ...", tupple.getMessage());
    Assert.assertNull("No throwable expected", tupple.getThrowable());
  }

  @Test
  public void testParamsFormatTwoParamsOneThrowable() throws Exception {
    Throwable throwable = new Throwable();
    LogTuple tupple = LogFormatter.format("the message pattern {} ...", "param1", throwable);
    Assert.assertEquals("the message pattern param1 ...", tupple.getMessage());
    Assert.assertEquals("Throwable expected", throwable, tupple.getThrowable());
  }

  @Test
  public void testParamsFormat10ParamsOneThrowable() throws Exception {
    Throwable throwable = new Throwable();
    LogTuple tupple = LogFormatter.format("the message pattern {} {} {} {} {} {} {} {} {} ...", new Object[]{"param1", "param2", "param3", "param4", "param5", "param6", "param7", "param8", "param9", throwable});
    Assert.assertEquals("the message pattern param1 param2 param3 param4 param5 param6 param7 param8 param9 ...", tupple.getMessage());
    Assert.assertEquals("Throwable expected", throwable, tupple.getThrowable());
  }
  @Test
  public void testParamsFormat10ParamsNoThrowable() throws Exception {
    LogTuple tupple = LogFormatter.format("the message pattern {} {} {} {} {} {} {} {} {} {} ...", new Object[]{"param1", "param2", "param3", "param4", "param5", "param6", "param7", "param8", "param9", "param10"});
    Assert.assertEquals("the message pattern param1 param2 param3 param4 param5 param6 param7 param8 param9 param10 ...", tupple.getMessage());
    Assert.assertNull("No throwable expected", tupple.getThrowable());
  }
  @Test
  public void testParamsFormat10ParamsAsStringNoThrowable() throws Exception {
    LogTuple tupple = LogFormatter.format("the message pattern {} {} {} {} {} {} {} {} {} {} ...", new String[]{"param1", "param2", "param3", "param4", "param5", "param6", "param7", "param8", "param9", "param10"});
    Assert.assertEquals("the message pattern param1 param2 param3 param4 param5 param6 param7 param8 param9 param10 ...", tupple.getMessage());
    Assert.assertNull("No throwable expected", tupple.getThrowable());
  }
  @Test
  public void testParamsFormat6ParamsMixNoThrowable() throws Exception {
    LogTuple tupple = LogFormatter.format("the message pattern {} {} {} {} {} {} ...", new Object[]{"param1", 2, 2.3, 200000000000000000l, 2.9999999999, new String[]{"param7", "param8", "param9", "param10"}});
    Assert.assertEquals("the message pattern param1 2 2.3 200000000000000000 2.9999999999 [param7, param8, param9, param10] ...", tupple.getMessage());
    Assert.assertNull("No throwable expected", tupple.getThrowable());
  }
}
