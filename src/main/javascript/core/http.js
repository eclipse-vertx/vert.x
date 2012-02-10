var vertx = vertx || {};

if (!vertx.HttpServer) {

  function convertMap(j_map) {
    var map = {};
    var j_iter = j_map.entrySet().iterator();
    while (j_iter.hasNext()) {
      var entry = j_iter.next();
      map[entry.getKey()] = entry.getValue();
    }
    return map;
  }

  vertx.HttpServer = function() {

    var j_server = new org.vertx.java.core.http.HttpServer();

    var that = this;

    this.requestHandler = function(handler) {

      if (handler) {

        var theHandler;
        if (handler.handle) {

          theHandler = function(req) {
            handler.handle(req);
          }

        } else {
          theHandler = handler;
        }

        var wrappedHandler = function(req) {

          //We need to add some functions to the request and the response

          var reqHeaders = null;
          var reqParams = null;

          var reqProto = {

            headers: function() {
              if (!reqHeaders) {
                reqHeaders = convertMap(req.getAllHeaders());
              }
              return reqHeaders;
            },
            params: function() {
              if (!reqParams) {
                reqParams = convertMap(req.getAllParams());
              }
              return reqParams;
            }
          };

          var respProto = {
            putHeaders: function(hash) {
              for (k in hash) {
                req.response.putHeader(k, hash[k]);
              }
            },
            putTrailers: function(hash) {
              for (k in hash) {
                req.response.putTrailer(k, hash[k]);
              }
            }
          }

          req.__proto__ = reqProto;
          req.response.__proto__ = respProto;

          theHandler(req);
        }

        j_server.requestHandler(wrappedHandler);
      }
      return that;
    };

    this.websocketHandler = function(handler) {
      if (handler) {
        j_server.websocketHandler(handler);
      }
      return that;
    };

    this.close = function(handler) {
      if (handler) {
        j_server.close(handler);
      } else {
        j_server.close();
      }
    };

    this.listen = function(port, host) {
      if (host) {
        j_server.listen(port, host);
      } else {
        j_server.listen(port);
      }
      return that;
    }

    that.setSSL = function(ssl) {
      j_server.setSSL(ssl);
      return that;
    }

    that.setKeyStorePath = function(path) {
      j_server.setKeyStorePath(path);
      return that;
    }

    that.setKeyStorePassword = function(password) {
      j_server.setKeyStorePassword(password);
      return that;
    }

    that.setTrustStorePath = function(path) {
      j_server.setTrustStorePath(path);
      return that;
    }

    that.setTrustStorePassword = function(password) {
      j_server.setTrustStorePassword(password);
      return that;
    }

    that.setClientAuthRequired = function(required) {
      j_server.setClientAuthRequired(required);
      return that;
    }

    that.setTCPNoDelay = function(tcpNoDelay) {
      j_server.setTCPNoDelay(tcpNoDelay);
      return that;
    }

    that.setSendBufferSize = function(size) {
      j_server.setSendBufferSize(size);
      return that;
    }

    that.setReceiveBufferSize = function(size) {
      j_server.setReceiveBufferSize(size);
      return that;
    }

    that.setTCPKeepAlive = function(keepAlive) {
      j_server.setTCPKeepAlive(keepAlive);
      return that;
    }

    that.setReuseAddress = function(reuse) {
      j_server.setReuseAddress(reuse);
      return that;
    }

    that.setSoLinger = function(linger) {
      j_server.setSoLinger(linger);
      return that;
    }

    that.setTrafficClass = function(class) {
      j_server.setTrafficClass(class);
      return that;
    }

    that.setClientAuthRequired = function(required) {
      j_server.setClientAuthRequired(required);
      return that;
    }

    that.isTCPNoDelay = function() {
      return j_server.isTCPNoDelay();
    }

    that.getSendBufferSize = function() {
      return j_server.getSendBufferSize();
    }

    that.getReceiveBufferSize = function() {
      return j_server.getReceiveBufferSize();
    }

    that.isSoLinger = function() {
      return j_server.isSoLinger();
    }

    that.getTrafficClass = function() {
      return j_server.getTrafficClass();
    }

    that.isSSL = function() {
      return j_server.isSSL();
    }
  }

  vertx.HttpClient = function() {
    var j_client = new org.vertx.java.core.http.HttpClient();
    
    var that = this;

    function wrapResponseHandler(handler) {
      var wrapperHandler = function(resp) {

        var respHeaders = null;
        var respTrailers = null;

        var resp_proto = {

          headers: function() {
            if (!respHeaders) {
              respHeaders = convertMap(resp.getAllHeaders());
            }
            return respHeaders;
          },
          trailers: function() {
            if (!respTrailers) {
              respTrailers = convertMap(resp.getAllTrailers());
            }
            return respTrailers;
          }
        };

        resp.__proto__ = resp_proto;

        handler(resp);
      }
      return wrapperHandler;
    }
    
    that.exceptionHandler = function(handler) {
      j_client.exceptionHandler(handler);
      return that;
    }
    
    that.setMaxPoolSize = function(size) {
      j_client.setMaxPoolSize(size);
      return that;
    }
    
    that.getMaxPoolSize = function() {
      return j_client.getMaxPoolSize();
    }
    
    that.setKeepAlive = function(keepAlive) {
      j_client.setKeepAlive(keepAlive);
      return that;
    }
    
    that.setSSL = function(ssl) {
      j_client.setSSL(ssl);
      return that;
    }

    that.setKeyStorePath = function(path) {
      j_client.setKeyStorePath(path);
      return that;
    }

    that.setKeyStorePassword = function(password) {
      j_client.setKeyStorePassword(password);
      return that;
    }

    that.setTrustStorePath = function(path) {
      j_client.setTrustStorePath(path);
      return that;
    }

    that.setTrustStorePassword = function(password) {
      j_client.setTrustStorePassword(password);
      return that;
    }
    
    that.setTrustAll = function(trustAll) {
      j_client.setTrustAll(trustAll);
      return that;
    }
    
    that.setPort = function(port) {
      j_client.setPort(port);
      return that;
    }
    
    that.setHost = function(host) {
      j_client.setHost(host);
      return that;
    }
    
    that.connectWebsocket = function(uri, handler) {
      j_client.connectWebsocket(uri, handler);
    }
    
    that.getNow = function(uri, handler) {
      return j_client.getNow(uri, wrapResponseHandler(handler));
    }
    
    that.options = function(uri, handler) {
      return j_client.options(uri, wrapResponseHandler(handler));
    }
    
    that.get = function(uri, handler) {
      return j_client.get(uri, wrapResponseHandler(handler));
    }
    
    that.head = function(uri, handler) {
      return j_client.head(uri, wrapResponseHandler(handler));
    }
    
    that.post = function(uri, handler) {
      return j_client.post(uri, wrapResponseHandler(handler));
    }
    
    that.put = function(uri, handler) {
      return j_client.put(uri, wrapResponseHandler(handler));
    }
    
    that.delete = function(uri, handler) {
      return j_client.delete(uri, wrapResponseHandler(handler));
    }
    
    that.trace = function(uri, handler) {
      return j_client.trace(uri, wrapResponseHandler(handler));
    }
    
    that.connect = function(uri, handler) {
      return j_client.connect(uri, wrapResponseHandler(handler));
    }
    
    that.patch = function(uri, handler) {
      return j_client.patch(uri, wrapResponseHandler(handler));
    }
    
    that.request = function(method, uri, handler) {
      return j_client.request(method, uri, wrapResponseHandler(handler));
    }
    
    that.close = function() {
      j_client.close();
    }
    
    that.setTCPNoDelay = function(tcpNoDelay) {
      j_client.setTCPNoDelay(tcpNoDelay);
      return that;
    }

    that.setSendBufferSize = function(size) {
      j_client.setSendBufferSize(size);
      return that;
    }

    that.setReceiveBufferSize = function(size) {
      j_client.setReceiveBufferSize(size);
      return that;
    }

    that.setTCPKeepAlive = function(keepAlive) {
      j_client.setTCPKeepAlive(keepAlive);
      return that;
    }

    that.setReuseAddress = function(reuse) {
      j_client.setReuseAddress(reuse);
      return that;
    }

    that.setSoLinger = function(linger) {
      j_client.setSoLinger(linger);
      return that;
    }

    that.setTrafficClass = function(class) {
      j_client.setTrafficClass(class);
      return that;
    }
    
    that.isTCPNoDelay = function() {
      return j_client.isTCPNoDelay();
    }

    that.getSendBufferSize = function() {
      return j_client.getSendBufferSize();
    }

    that.getReceiveBufferSize = function() {
      return j_client.getReceiveBufferSize();
    }

    that.isSoLinger = function() {
      return j_client.isSoLinger();
    }

    that.getTrafficClass = function() {
      return j_client.getTrafficClass();
    }

    that.isSSL = function() {
      return j_client.isSSL();
    }
    
  }

  vertx.RouteMatcher = function() {
    return new org.vertx.java.core.http.RouteMatcher();
  }
}
