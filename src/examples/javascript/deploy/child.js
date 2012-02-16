load('vertx.js')

var log = vertx.getLogger();

log.info("in child.js, config is " + vertx.getConfig());

log.info(JSON.stringify(vertx.getConfig()));