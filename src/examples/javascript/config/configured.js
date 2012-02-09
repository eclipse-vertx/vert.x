load('vertx.js')

var config = vertx.getConfig();

log.println('Config is ' + JSON.stringify(config));