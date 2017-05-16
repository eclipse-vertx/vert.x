/*
 * Copyright (c) 2011-2014 The original author or authors
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
package io.vertx.core.json;

import io.vertx.core.ServiceHelper;

import java.util.Objects;

/**
 * Abstraction for Json related functionality used by the API. Implementations must register using the
 * {@link io.vertx.core.ServiceHelper} mechanism (by default based on {@link java.util.ServiceLoader}.
 */
public interface JsonServices {
    /** The current instance loaded. */
    static final JsonServices INSTANCE = ServiceHelper.loadFactory(JsonServices.class);

    /**
     * Accessor for getting the singleton instane.
     * @return the services instance, not null.
     */
    public static JsonServices of(){
        return Objects.requireNonNull(INSTANCE, "No JsonServices loaded.");
    }

    /**
     * Checks the given object and optionally creates a copy of it.
     * @param val the object to check.
     * @param copy flag, if a copy should be created.
     * @return the instance passed, or copied.
     */
    Object checkAndCopy(Object val, boolean copy);

    /**
     * Encodes the given object.
     * @param obj the instance to encode, not null.
     * @return the encoded value
     * @throws io.vertx.core.json.EncodeException
     */
    String encode(Object obj) throws EncodeException;

    /**
     * Encodes the given object, prettifying the output for better readability.
     * @param obj the instance to encode, not null.
     * @return the encoded value
     * @throws io.vertx.core.json.EncodeException
     */
    String encodePrettily(Object obj) throws EncodeException;

    /**
     * Decodes the given Json input into the required target type.
     * @param json the JSON input
     * @param clazz the target type
     * @param <T> the type to be returned.
     * @return the decoded instance, not null.
     * @throws io.vertx.core.json.DecodeException
     */
    <T> T decodeValue(String json, Class<?> clazz) throws DecodeException;
}
