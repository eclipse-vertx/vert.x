/*
 * Copyright (c) 2011-2013 The original author or authors
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
package org.vertx.java.core.dns;

/**
 * Represent a Service-Record (SRV) which was resolved for a domain.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface SrvRecord {

    /**
     * Returns the priority for this service record.
     */
    public int priority();

    /**
     * Returns the weight of this service record.
     */
    public int weight();

    /**
     * Returns the port the service is running on.
     */
    public int port();

    /**
     * Returns the name for the server being queried.
     */
    public String name();

    /**
     * Returns the protocol for the service being queried (i.e. "_tcp").
     */
    public String protocol();

    /**
     * Returns the service's name (i.e. "_http").
     */
    public String service();

    /**
     * Returns the name of the host for the service.
     */
    public String target();
}
