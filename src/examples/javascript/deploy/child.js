load('vertx.js')

var log = vertx.logger;

log.info("in child.js, config is " + vertx.config);

log.info(JSON.stringify(vertx.config));