/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.vertx.java.core.http.ws;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;

/**
 * Abstraction of web-socket handshake versions.
 * 
 * <p>
 * Since each version uses different headers and behaves differently, these
 * differences are encapsulated in subclasses of <code>Handshake</code>.
 * </p>
 *
 * 
 * @author Bob McWhirter
 */
public abstract class Handshake {

    public Handshake(String version) {
        this.version = version;
    }

    public String getVersion() {
        return this.version;
    }

    protected String getWebSocketLocation(HttpRequest request) {
        return "ws://" + request.getHeader( HttpHeaders.Names.HOST ) + request.getUri();
    }

    public abstract boolean matches(HttpRequest request);

    public abstract void generateRequest(HttpClientRequest req, String hostHeader) throws Exception;
    public abstract HttpResponse generateResponse(HttpRequest request) throws Exception;
    public abstract boolean isComplete(HttpClientResponse response) throws Exception;
    
    public abstract ChannelHandler newEncoder();
    public abstract ChannelHandler newDecoder();
    public abstract ChannelHandler[] newAdditionalHandlers();
    
    public abstract int readResponseBody();

    private String version;

}
