vertx = require('vertx');

module.exports = function(port, host){
    var server = vertx.createHttpServer();

    this.use = function(handler){
        server.requestHandler(handler);
    };

    this.start = function() {
        server.listen(port, host);
    };
};