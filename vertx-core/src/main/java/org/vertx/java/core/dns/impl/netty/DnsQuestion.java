/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.vertx.java.core.dns.impl.netty;

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
     * @param name
     *            the domain name being queried i.e. "www.example.com"
     * @param type
     *            the question type, which represents the type of
     *            {@link DnsResource} record that should be returned
     */
    public DnsQuestion(String name, int type) {
        this(name, type, CLASS_IN);
    }

    /**
     * Constructs a question with the given class.
     *
     * @param name
     *            the domain name being queried i.e. "www.example.com"
     * @param type
     *            the question type, which represents the type of
     *            {@link DnsResource} record that should be returned
     * @param qClass
     *            the class of a DNS record
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
