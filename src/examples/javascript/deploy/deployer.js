load('vertx.js')

log.println('in deployer');

var config = {name: 'tim', age: 823823};

var id = vertx.deployVerticle('deploy/child.js', config, '.', 1);

log.println('deployed with id ' + id);

vertx.exit();

function vertxStop() {
  log.println("stop called in deployer.js")
}