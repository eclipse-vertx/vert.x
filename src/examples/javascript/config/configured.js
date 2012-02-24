load('vertx.js')

var log = vertx.logger;

log.info('Config is ' + JSON.stringify(vertx.config));