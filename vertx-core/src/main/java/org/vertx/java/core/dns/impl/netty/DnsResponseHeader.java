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
 * The DNS response header class which is used when receiving data from a DNS
 * server. Contains information contained in a DNS response header, such as
 * recursion availability, and response codes.
 */
public class DnsResponseHeader extends DnsHeader {

    private int readQuestions;
    private int readAnswers;
    private int readAuthorityResources;
    private int readAdditionalResources;

    private boolean authoritativeAnswer;
    private boolean truncated;
    private boolean recursionAvailable;

    private int z;
    private int responseCode;

    /**
     * Constructor for a DNS packet response header. The id is received by
     * reading a {@link DnsQuery} and is sent back to the client.
     *
     * @param parent
     *            the {@link DnsMessage} this header belongs to
     * @param id
     *            a 2 bit unsigned identification number received from client
     */
    public DnsResponseHeader(DnsMessage<? extends DnsResponseHeader> parent, int id) {
        super(parent);
        setId(id);
        setType(TYPE_RESPONSE);
    }

    /**
     * Returns {@code true} if responding server is authoritative for the domain
     * name in the query message.
     */
    public boolean isAuthoritativeAnswer() {
        return authoritativeAnswer;
    }

    /**
     * Returns {@code true} if response has been truncated, usually if it is
     * over 512 bytes.
     */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * Returns {@code true} if DNS server can handle recursive queries.
     */
    public boolean isRecursionAvailable() {
        return recursionAvailable;
    }

    /**
     * Returns the 3 bit reserved field 'Z'.
     */
    public int getZ() {
        return z;
    }

    /**
     * Returns the 4 bit return code. Response codes outlined in
     * {@link ReturnCode}.
     */
    public int getResponseCode() {
        return responseCode;
    }

    /**
     * Returns the number of questions to read for this DNS response packet.
     */
    public int getReadQuestions() {
        return readQuestions;
    }

    /**
     * Returns the number of answers to read for this DNS response packet.
     */
    public int getReadAnswers() {
        return readAnswers;
    }

    /**
     * Returns the number of authority resource records to read for this DNS
     * response packet.
     */
    public int getReadAuthorityResources() {
        return readAuthorityResources;
    }

    /**
     * Returns the number of additional resource records to read for this DNS
     * response packet.
     */
    public int getReadAdditionalResources() {
        return readAdditionalResources;
    }

    /**
     * Returns the {@link DnsMessage} type. This will always return
     * {@code TYPE_RESPONSE}.
     */
    @Override
    public final int getType() {
        return TYPE_RESPONSE;
    }

    /**
     * Set to {@code true} if responding server is authoritative for the domain
     * name in the query message.
     *
     * @param authoritativeAnswer
     *            flag for authoritative answer
     */
    public void setAuthoritativeAnswer(boolean authoritativeAnswer) {
        this.authoritativeAnswer = authoritativeAnswer;
    }

    /**
     * Set to {@code true} if response has been truncated (usually happens for
     * responses over 512 bytes).
     *
     * @param truncated
     *            flag for truncation
     */
    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    /**
     * Set to {@code true} if DNS server can handle recursive queries.
     *
     * @param recursionAvailable
     *            flag for recursion availability
     */
    public void setRecursionAvailable(boolean recursionAvailable) {
        this.recursionAvailable = recursionAvailable;
    }

    /**
     * Sets the field Z. This field is reserved and should remain as 0 if the
     * DNS server does not make usage of this field.
     *
     * @param z
     *            the value for the reserved field Z
     */
    public void setZ(int z) {
        this.z = z;
    }

    /**
     * Sets the response code for this message.
     *
     * @param responseCode
     *            the response code
     */
    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    /**
     * Sets the number of questions that should be read after this header has
     * been decoded.
     *
     * @param readQuestions
     *            the number of questions to read
     */
    public void setReadQuestions(int readQuestions) {
        this.readQuestions = readQuestions;
    }

    /**
     * Sets the number of answers that should be read after this header has been
     * decoded.
     *
     * @param readAnswers
     *            the number of answers to read
     */
    public void setReadAnswers(int readAnswers) {
        this.readAnswers = readAnswers;
    }

    /**
     * Sets the number of authority resources to be read after this header has
     * been decoded.
     *
     * @param readAuthorityResources
     *            the number of authority resources to read
     */
    public void setReadAuthorityResources(int readAuthorityResources) {
        this.readAuthorityResources = readAuthorityResources;
    }

    /**
     * Sets the number of additional resources to be read after this header has
     * been decoded.
     *
     * @param readAdditionalResources
     *            the number of additional resources to read
     */
    public void setReadAdditionalResources(int readAdditionalResources) {
        this.readAdditionalResources = readAdditionalResources;
    }

    /**
     * Sets the {@link DnsHeader} type. Must be {@code TYPE_RESPONSE}.
     *
     * @param type
     *            message type
     * @return the header to allow method chaining
     */
    @Override
    public final DnsResponseHeader setType(int type) {
        if (type != TYPE_RESPONSE) {
            throw new IllegalArgumentException("type cannot be anything but TYPE_RESPONSE (1) for a response header.");
        }
        super.setType(type);
        return this;
    }

}
