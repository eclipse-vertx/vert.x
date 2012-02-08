load('vertx.js')

log.println("in child.js, config is " + vertx.getConfig());

log.println(JSON.stringify(vertx.getConfig()));