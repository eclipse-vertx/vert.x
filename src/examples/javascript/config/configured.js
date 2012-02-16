load('vertx.js')

var config = vertx.getConfig();

var log = vertx.getLogger();

log.info('Config is ' + JSON.stringify(config));