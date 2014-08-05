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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The message super-class which contains core information concerning DNS
 * packets, both outgoing and incoming.
 */
public abstract class DnsMessage<H extends DnsHeader> {

  private final List<DnsQuestion> questions = new ArrayList<DnsQuestion>();
  private final List<DnsResource> answers = new ArrayList<DnsResource>();
  private final List<DnsResource> authority = new ArrayList<DnsResource>();
  private final List<DnsResource> additional = new ArrayList<DnsResource>();

  private H header;

  /**
   * Returns the header belonging to this message.
   */
  public H getHeader() {
    return header;
  }

  /**
   * Returns a list of all the questions in this message.
   */
  public List<DnsQuestion> getQuestions() {
    return Collections.unmodifiableList(questions);
  }

  /**
   * Returns a list of all the answer resource records in this message.
   */
  public List<DnsResource> getAnswers() {
    return Collections.unmodifiableList(answers);
  }

  /**
   * Returns a list of all the authority resource records in this message.
   */
  public List<DnsResource> getAuthorityResources() {
    return Collections.unmodifiableList(authority);
  }

  /**
   * Returns a list of all the additional resource records in this message.
   */
  public List<DnsResource> getAdditionalResources() {
    return Collections.unmodifiableList(additional);
  }

  /**
   * Adds an answer resource record to this message.
   *
   * @param answer the answer resource record to be added
   * @return the message to allow method chaining
   */
  public DnsMessage<H> addAnswer(DnsResource answer) {
    answers.add(answer);
    return this;
  }

  /**
   * Adds a question to this message.
   *
   * @param question the question to be added
   * @return the message to allow method chaining
   */
  public DnsMessage<H> addQuestion(DnsQuestion question) {
    questions.add(question);
    return this;
  }

  /**
   * Adds an authority resource record to this message.
   *
   * @param resource the authority resource record to be added
   * @return the message to allow method chaining
   */
  public DnsMessage<H> addAuthorityResource(DnsResource resource) {
    authority.add(resource);
    return this;
  }

  /**
   * Adds an additional resource record to this message.
   *
   * @param resource the additional resource record to be added
   * @return the message to allow method chaining
   */
  public DnsMessage<H> addAdditionalResource(DnsResource resource) {
    additional.add(resource);
    return this;
  }

  /**
   * Sets this message's {@link DnsHeader}.
   *
   * @param header the header being attached to this message
   * @return the message to allow method chaining
   */
  public DnsMessage<H> setHeader(H header) {
    this.header = header;
    return this;
  }

}
