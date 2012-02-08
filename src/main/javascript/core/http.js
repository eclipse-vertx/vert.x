var vertx = vertx || {};

if (!vertx.HttpServer) {
  vertx.HttpServer = function() {

    function convertMap(j_map) {
      var map = {};
      var j_iter = j_map.entrySet().iterator();
      while (j_iter.hasNext()) {
        var entry = j_iter.next();
        map[entry.getKey()] = entry.getValue();
      }
      return map;
    }

    var s = new JavaAdapter(org.vertx.java.core.http.HttpServer, {
      requestHandler: function(handler) {

        if (!handler) {
          return this.super$requestHandler();
        }

        this.super$requestHandler(function(j_req) {

          var headers = null;
          var params = null;

          var wrappedResp = {
            putHeaders: function(hash) {
              for (k in hash) {
                j_req.response.putHeader(k, hash[k]);
              }
            },
            putTrailers: function(hash) {
              for (k in hash) {
                j_req.response.putTrailer(k, hash[k]);
              }
            }
          };

          wrappedResp.__proto__ = j_req.response;

          var wrappedReq = {
            headers: function() {
              if (!headers) {
                headers = convertMap(j_req.getHeaders());
              }
              return headers;
            },
            params: function() {
              if (!params) {
                params = convertMap(j_req.getParams());
              }
              return params;
            },
            response: wrappedResp
          };

          wrappedReq.__proto__ = j_req;

          handler.handle(wrappedReq);
        });

        return s;
      }
    });

    return s;
  }

  vertx.HttpClient = function() {
    return new org.vertx.java.core.http.HttpClient();
  }

  vertx.RouteMatcher = function() {
    return new org.vertx.java.core.http.RouteMatcher();
  }
}
