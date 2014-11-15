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
package io.vertx.core.dns.impl.netty.decoder.record;

/**
 * Represents an MX (mail exchanger) record, which contains a mail server
 * responsible for accepting e-mail and a preference value for prioritizing mail
 * servers if multiple servers exist.
 */
public class MailExchangerRecord {

    private final int priority;
    private final String name;

    /**
     * Constructs an MX (mail exchanger) record.
     *
     * @param priority
     *            the priority of the mail exchanger, lower is more preferred
     * @param name
     *            the e-mail address in the format admin.example.com, which
     *            represents admin@example.com
     */
    public MailExchangerRecord(int priority, String name) {
        this.priority = priority;
        this.name = name;
    }

    /**
     * Returns the priority of the mail exchanger, lower is more preferred.
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns the mail exchanger (an e-mail address) in the format
     * admin.example.com, which represents admin@example.com.
     */
    public String name() {
        return name;
    }

}
