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
 * A DNS query packet which is sent to a server to receive a DNS response packet
 * with information answering a DnsQuery's questions.
 */
public class DnsQuery extends DnsMessage<DnsQueryHeader> {

    /**
     * Constructs a DNS query. By default recursion will be toggled on.
     */
    public DnsQuery(int id) {
        setHeader(new DnsQueryHeader(this, id));
    }

}
