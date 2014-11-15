/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class RepeatRule implements TestRule {

  private static class RepeatStatement extends Statement {

    private final int times;
    private final Statement statement;

    private RepeatStatement( int times, Statement statement ) {
      this.times = times;
      this.statement = statement;
    }

    @Override
    public void evaluate() throws Throwable {
      for( int i = 0; i < times; i++ ) {
        System.out.println("*** Iteration " + (i + 1) + "/" + times + " of test");
        statement.evaluate();
      }
    }
  }

  @Override
  public Statement apply( Statement statement, Description description ) {
    Statement result = statement;
    Repeat repeat = description.getAnnotation(Repeat.class);
    if( repeat != null ) {
      int times = repeat.times();
      result = new RepeatStatement( times, statement );
    }
    return result;
  }
}
