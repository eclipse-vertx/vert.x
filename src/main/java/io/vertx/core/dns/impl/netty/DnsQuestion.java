/*
 * Copyright (c) 2013 The Netty Project
 * ------------------------------------
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
package io.vertx.core.dns.impl.netty;

/**
 * The DNS question class which represents a question being sent to a server via
 * a query, or the question being duplicated and sent back in a response.
 * Usually a message contains a single question, and DNS servers often don't
 * support multiple questions in a single query.
 */
public class DnsQuestion extends DnsEntry {

  /**
   * Constructs a question with the default class IN (Internet).
   *
   * @param name the domain name being queried i.e. "www.example.com"
   * @param type the question type, which represents the type of
   *             {@link DnsResource} record that should be returned
   */
  public DnsQuestion(String name, int type) {
    this(name, type, CLASS_IN);
  }

  /**
   * Constructs a question with the given class.
   *
   * @param name   the domain name being queried i.e. "www.example.com"
   * @param type   the question type, which represents the type of
   *               {@link DnsResource} record that should be returned
   * @param qClass the class of a DNS record
   */
  public DnsQuestion(String name, int type, int qClass) {
    super(name, type, qClass);
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (other instanceof DnsQuestion) {
      DnsQuestion question = (DnsQuestion) other;
      return question.name().equals(name()) && question.type() == type() && question.dnsClass() == dnsClass();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return ((name().hashCode() + type()) * 7 + dnsClass()) * 7;
  }

}
