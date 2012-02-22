load('vertx.js')

var config = vertx.getConfig();

var log = vertx.logger;

log.info('Config is ' + JSON.stringify(config));