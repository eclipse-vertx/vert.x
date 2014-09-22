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

import java.util.Objects;

/**
 * The header super-class which includes information shared by DNS query and
 * response packet headers such as the ID, opcode, and type. The only flag
 * shared by both classes is the flag for desiring recursion.
 */
public class DnsHeader {

  /**
   * Message type is query.
   */
  public static final int TYPE_QUERY = 0;

  /**
   * Message type is response.
   */
  public static final int TYPE_RESPONSE = 1;

  /**
   * Message is for a standard query.
   */
  public static final int OPCODE_QUERY = 0;

  /**
   * Message is for an inverse query. <strong>Note: inverse queries have been
   * obsoleted since RFC 3425, and are not necessarily supported.</strong>
   */
  @Deprecated
  public static final int OPCODE_IQUERY = 1;

  private final DnsMessage<? extends DnsHeader> parent;

  private boolean recursionDesired;
  private int opcode;
  private int id;
  private int type;

  public DnsHeader(DnsMessage<? extends DnsHeader> parent) {
    Objects.requireNonNull(parent, "the parent field cannot be null and must point to a valid DnsMessage.");
    this.parent = parent;
  }

  /**
   * Returns the number of questions in the {@link DnsMessage}.
   */
  public int questionCount() {
    return parent.getQuestions().size();
  }

  /**
   * Returns the number of answer resource records in the {@link DnsMessage}.
   */
  public int answerCount() {
    return parent.getAnswers().size();
  }

  /**
   * Returns the number of authority resource records in the
   * {@link DnsMessage}.
   */
  public int authorityResourceCount() {
    return parent.getAuthorityResources().size();
  }

  /**
   * Returns the number of additional resource records in the
   * {@link DnsMessage}.
   */
  public int additionalResourceCount() {
    return parent.getAdditionalResources().size();
  }

  /**
   * Returns {@code true} if a query is to be pursued recursively.
   */
  public boolean isRecursionDesired() {
    return recursionDesired;
  }

  /**
   * Returns the 4 bit opcode used for the {@link DnsMessage}.
   *
   * @see #OPCODE_QUERY
   * @see #OPCODE_IQUERY
   */
  public int getOpcode() {
    return opcode;
  }

  /**
   * Returns the type of {@link DnsMessage}.
   *
   * @see #TYPE_QUERY
   */
  public int getType() {
    return type;
  }

  /**
   * Returns the 2 byte unsigned identifier number used for the
   * {@link DnsMessage}.
   */
  public int getId() {
    return id;
  }

  /**
   * Sets the opcode for this {@link DnsMessage}.
   *
   * @param opcode opcode to set
   * @return the header to allow method chaining
   */
  public DnsHeader setOpcode(int opcode) {
    this.opcode = opcode;
    return this;
  }

  /**
   * Sets whether a name server is directed to pursue a query recursively or
   * not.
   *
   * @param recursionDesired if set to {@code true}, pursues query recursively
   * @return the header to allow method chaining
   */
  public DnsHeader setRecursionDesired(boolean recursionDesired) {
    this.recursionDesired = recursionDesired;
    return this;
  }

  /**
   * Sets the {@link DnsMessage} type.
   *
   * @param type message type
   * @return the header to allow method chaining
   */
  public DnsHeader setType(int type) {
    this.type = type;
    return this;
  }

  /**
   * Sets the id for this {@link DnsMessage}.
   *
   * @param id a unique 2 byte unsigned identifier
   * @return the header to allow method chaining
   */
  public DnsHeader setId(int id) {
    this.id = id;
    return this;
  }

}
