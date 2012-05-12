load('vertx.js')

var config = {name: 'tim', age: 823823};

var id = vertx.deployVerticle('deploy/child.js', config, 1);
