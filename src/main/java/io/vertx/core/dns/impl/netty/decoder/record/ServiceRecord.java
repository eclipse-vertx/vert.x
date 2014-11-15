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
 * Represents an SRV (service) record, which contains the location, or hostname
 * and port, of servers for specified services. For example, a service "http"
 * may be running on the server "example.com" on port 80.
 */
public class ServiceRecord {

    private final int priority;
    private final int weight;
    private final int port;
    private final String name;
    private final String protocol;
    private final String service;
    private final String target;

    /**
     * Constructs an SRV (service) record.
     *
     * @param fullPath
     *            the name first read in the SRV record which contains the
     *            service, the protocol, and the name of the server being
     *            queried. The format is {@code _service._protocol.hostname}, or
     *            for example {@code _http._tcp.example.com}
     * @param priority
     *            relative priority of this service, range 0-65535 (lower is
     *            higher priority)
     * @param weight
     *            determines how often multiple services will be used in the
     *            event they have the same priority (greater weight means
     *            service is used more often)
     * @param port
     *            the port for the service
     * @param target
     *            the name of the host for the service
     */
    public ServiceRecord(String fullPath, int priority, int weight, int port, String target) {
        String[] parts = fullPath.split("\\.", 3);
        service = parts[0];
        protocol = parts[1];
        name = parts[2];
        this.priority = priority;
        this.weight = weight;
        this.port = port;
        this.target = target;
    }

    /**
     * Returns the priority for this service record.
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns the weight of this service record.
     */
    public int weight() {
        return weight;
    }

    /**
     * Returns the port the service is running on.
     */
    public int port() {
        return port;
    }

    /**
     * Returns the name for the server being queried.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the protocol for the service being queried (i.e. "_tcp").
     */
    public String protocol() {
        return protocol;
    }

    /**
     * Returns the service's name (i.e. "_http").
     */
    public String service() {
        return service;
    }

    /**
     * Returns the name of the host for the service.
     */
    public String target() {
        return target;
    }

}
