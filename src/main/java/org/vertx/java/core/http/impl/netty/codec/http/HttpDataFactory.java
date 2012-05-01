/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.vertx.java.core.http.impl.netty.codec.http;

import java.nio.charset.Charset;

import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Interface to enable creation of InterfaceHttpData objects
 */
public interface HttpDataFactory {
    /**
    *
    * @param request associated request
    * @param name
    * @return a new Attribute with no value
    * @throws NullPointerException
    * @throws IllegalArgumentException
    */
    Attribute createAttribute(HttpRequest request, String name);

    /**
     *
     * @param request associated request
     * @param name
     * @param value
     * @return a new Attribute
     * @throws NullPointerException
     * @throws IllegalArgumentException
     */
    Attribute createAttribute(HttpRequest request, String name, String value);

    /**
     *
     * @param request associated request
     * @param name
     * @param filename
     * @param contentType
     * @param charset
     * @param size the size of the Uploaded file
     * @return a new FileUpload
     */
    FileUpload createFileUpload(HttpRequest request, String name, String filename,
                                String contentType, String contentTransferEncoding, Charset charset,
                                long size);

    /**
     * Remove the given InterfaceHttpData from clean list (will not delete the file, except if the file
     * is still a temporary one as setup at construction)
     * @param request associated request
     * @param data
     */
    void removeHttpDataFromClean(HttpRequest request, InterfaceHttpData data);

    /**
     * Remove all InterfaceHttpData from virtual File storage from clean list for the request
     *
     * @param request associated request
     */
    void cleanRequestHttpDatas(HttpRequest request);

    /**
     * Remove all InterfaceHttpData from virtual File storage from clean list for all requests
     */
    void cleanAllHttpDatas();
}
