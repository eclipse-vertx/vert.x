load('vertx.js');

function deploy_it(count) {
  vertx.deployVerticle('child.rb', null, 1, function(deploy_id) {
    console.log("deployed " + count);
    undeploy_it(deploy_id, count);
  });
}

function undeploy_it(deploy_id, count) {
  vertx.undeployVerticle(deploy_id, function() {
    count++;
    if (count < 10000) {
      deploy_it(count);
    } else {
      console.log("done!");
    }
  });
}

deploy_it(0)

