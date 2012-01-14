package org.vertx.java.tests.redis;

import org.junit.Test;
import org.vertx.java.core.app.AppType;
import org.vertx.java.newtests.TestBase;
import vertx.tests.redis.TestClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaRedisTest extends TestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    startApp(AppType.JAVA, TestClient.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testAppend() {
    startTest(getMethodName());
  }

  public void testBgWriteAOF() {
    startTest(getMethodName());
  }

  public void testBGSave() {
    startTest(getMethodName());
  }

  public void testBLPop() {
    startTest(getMethodName());
  }

  public void testBRPop() {
    startTest(getMethodName());
  }

  public void testBRPopLPush() {
    startTest(getMethodName());
  }

  public void testDBSize() {
    startTest(getMethodName());
  }

  public void testDecr() {
    startTest(getMethodName());
  }

  public void testDecrBy() {
    startTest(getMethodName());
  }

  public void testDel() {
    startTest(getMethodName());
  }

  public void testMultiDiscard() {
    startTest(getMethodName());
  }

  public void testEcho() {
    startTest(getMethodName());
  }

  public void testTransactionExec() {
    startTest(getMethodName());
  }

  public void testTransactionWithMultiBulkExec() {
    startTest(getMethodName());
  }

  public void testTransactionMixed() {
    startTest(getMethodName());
  }

  public void testTransactionDiscard() {
    startTest(getMethodName());
  }

  public void testEmptyTransactionExec() {
    startTest(getMethodName());
  }

  public void testEmptyTransactionDiscard() {
    startTest(getMethodName());
  }

  public void testPubSubPatterns() {
    startTest(getMethodName());
  }

  public void testPubSubOnlySubscribe() {
    startTest(getMethodName());
  }

  public void testPubSubSubscribeMultiple() {
    startTest(getMethodName());
  }

  public void testPooling1() {
    startTest(getMethodName());
  }

  public void testPooling2() {
    startTest(getMethodName());
  }

  public void testExists() {
    startTest(getMethodName());
  }

  public void testExpire() {
    startTest(getMethodName());
  }

  public void testExpireAt() {
    startTest(getMethodName());
  }

  public void testFlushAll() {
    startTest(getMethodName());
  }

  public void testFlushDB() {
    startTest(getMethodName());
  }

  public void testGet() {
    startTest(getMethodName());
  }

  public void testSetGetBit() {
    startTest(getMethodName());
  }

  public void testGetRange() {
    startTest(getMethodName());
  }

  public void testGetSet() {
    startTest(getMethodName());
  }

  public void testHdel() {
    startTest(getMethodName());
  }

  public void testHExists() {
    startTest(getMethodName());
  }

  public void testHGet() {
    startTest(getMethodName());
  }

  public void testHGetAll() {
    startTest(getMethodName());
  }

  public void testHIncrBy() {
    startTest(getMethodName());
  }

  public void testHKeys() {
    startTest(getMethodName());
  }

  public void testHLen() {
    startTest(getMethodName());
  }

  public void testHMGet() {
    startTest(getMethodName());
  }

  public void testHMSet() {
    startTest(getMethodName());
  }

  public void testHSet() {
    startTest(getMethodName());
  }

  public void testHSetNX() {
    startTest(getMethodName());
  }

  public void testHVals() {
    startTest(getMethodName());
  }

  public void testIncr() {
    startTest(getMethodName());
  }

  public void testIncrBy() {
    startTest(getMethodName());
  }

  public void testInfo() {
    startTest(getMethodName());
  }

  public void testKeys() {
    startTest(getMethodName());
  }

  public void testLastSave() {
    startTest(getMethodName());
  }

  public void testLIndex() {
    startTest(getMethodName());
  }

  public void testLInsert() {
    startTest(getMethodName());
  }

  public void testLLen() {
    startTest(getMethodName());
  }

  public void testLPop() {
    startTest(getMethodName());
  }

  public void testLPush() {
    startTest(getMethodName());
  }

  public void testLPushX() {
    startTest(getMethodName());
  }

  public void testLRange() {
    startTest(getMethodName());
  }

  public void testLRem() {
    startTest(getMethodName());
  }

  public void testLSet() {
    startTest(getMethodName());
  }

  public void testLTrim() {
    startTest(getMethodName());
  }

  public void testMGet() {
    startTest(getMethodName());
  }

  public void testMSet() {
    startTest(getMethodName());
  }

  public void testMSetNx() {
    startTest(getMethodName());
  }

  public void testPersist() {
    startTest(getMethodName());
  }

  public void testPing() {
    startTest(getMethodName());
  }

  public void testRandomKey() {
    startTest(getMethodName());
  }

  public void testRename() {
    startTest(getMethodName());
  }

  public void testRenameNx() {
    startTest(getMethodName());
  }

  public void testRPop() {
    startTest(getMethodName());
  }

  public void testRPopLPush() {
    startTest(getMethodName());
  }

  public void testRPush() {
    startTest(getMethodName());
  }

  public void testRPushX() {
    startTest(getMethodName());
  }

  public void testSAdd() {
    startTest(getMethodName());
  }

  public void testSave() {
    startTest(getMethodName());
  }

  public void testSCard() {
    startTest(getMethodName());
  }

  public void testSDiff() {
    startTest(getMethodName());
  }

  public void testSDiffStore() {
    startTest(getMethodName());
  }

  public void testSelect() {
    startTest(getMethodName());
  }

  public void testSet() {
    startTest(getMethodName());
  }

  public void testSetEx() {
    startTest(getMethodName());
  }

  public void testSetNx() {
    startTest(getMethodName());
  }

  public void testSetRange() {
    startTest(getMethodName());
  }

  public void testSInter() {
    startTest(getMethodName());
  }

  public void testSInterStore() {
    startTest(getMethodName());
  }

  public void testSIsMember() {
    startTest(getMethodName());
  }

  public void testSMembers() {
    startTest(getMethodName());
  }

  public void testSMove() {
    startTest(getMethodName());
  }

  public void testSortAscending() {
    startTest(getMethodName());
  }

  public void testSortDescending() {
    startTest(getMethodName());
  }

  public void testSortAlpha() {
    startTest(getMethodName());
  }

  public void testSortLimit() {
    startTest(getMethodName());
  }

  public void testSPop() {
    startTest(getMethodName());
  }

  public void testSRandMember() {
    startTest(getMethodName());
  }

  public void testSRem() {
    startTest(getMethodName());
  }

  public void testStrLen() {
    startTest(getMethodName());
  }

  public void testSUnion() {
    startTest(getMethodName());
  }

  public void testSUnionStore() {
    startTest(getMethodName());
  }

  public void testTTL() {
    startTest(getMethodName());
  }

  public void testType() {
    startTest(getMethodName());
  }

  public void testWatchUnWatch() {
    startTest(getMethodName());
  }

  public void testZAdd() {
    startTest(getMethodName());
  }

  public void testZCard() {
    startTest(getMethodName());
  }

  public void testZCount() {
    startTest(getMethodName());
  }

  public void testZIncrBy() {
    startTest(getMethodName());
  }

  public void testZInterStore() {
    startTest(getMethodName());
  }

  public void testZRange() {
    startTest(getMethodName());
  }

  public void testZRangeByScore() {
    startTest(getMethodName());
  }

  public void testZRank() {
    startTest(getMethodName());
  }

  public void testZRem() {
    startTest(getMethodName());
  }

  public void testZRemRangeByRank() {
    startTest(getMethodName());
  }

  public void testZRevRange() {
    startTest(getMethodName());
  }

  public void testZRevRangeByScore() {
    startTest(getMethodName());
  }

  public void testZRevRank() {
    startTest(getMethodName());
  }

  public void testZScore() {
    startTest(getMethodName());
  }

  public void testZUnionStore() {
    startTest(getMethodName());
  }

  public void testTestWithPassword() {
    startTest(getMethodName());
  }

}
