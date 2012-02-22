load('vertx.js')

var log = vertx.logger;

log.info("in child.js, config is " + vertx.getConfig());

log.info(JSON.stringify(vertx.getConfig()));