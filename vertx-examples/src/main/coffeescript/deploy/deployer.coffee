load('vertx.js')

config =
  name: 'tim'
  age: 823823

id = vertx.deployVerticle 'deploy/child.coffee', config, 1
