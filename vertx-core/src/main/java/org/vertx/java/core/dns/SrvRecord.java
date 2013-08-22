/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
