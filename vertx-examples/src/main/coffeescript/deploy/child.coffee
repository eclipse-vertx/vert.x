load('vertx.js')

log = vertx.logger
log.info "in child.coffee, config is #{vertx.config}"
log.info JSON.stringify(vertx.config)
