package org.vertx.java.newtests;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EventFields {

  public static final String TYPE_FIELD = "type";

  public static final String EXCEPTION_EVENT = "exception";

  public static final String EXCEPTION_MESSAGE_FIELD = "message";
  public static final String EXCEPTION_STACKTRACE_FIELD = "stacktrace";

  public static final String TRACE_EVENT = "trace";
  public static final String TRACE_MESSAGE_FIELD = "message";


  public static final String ASSERT_EVENT = "assert";
  public static final String ASSERT_RESULT_FIELD = "result";
  public static final String ASSERT_RESULT_VALUE_PASS = "pass";
  public static final String ASSERT_RESULT_VALUE_FAIL = "fail";
  public static final String ASSERT_MESSAGE_FIELD = "message";
  public static final String ASSERT_STACKTRACE_FIELD = "stacktrace";


  public static final String APP_READY_EVENT = "app_ready";
  public static final String APP_STOPPED_EVENT = "app_stopped";

  public static final String TEST_COMPLETE_EVENT = "test_complete";
  public static final String TEST_COMPLETE_NAME_FIELD = "name";

  public static final String START_TEST_EVENT = "start_test";
  public static final String START_TEST_NAME_FIELD = "name";

}
