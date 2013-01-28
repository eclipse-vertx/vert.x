load('vertx.js');

function deploy_it(count) {
  vertx.deployVerticle('child' + count + '.js', null, 1, function(deploy_id) {
    console.log("deployed " + count);
    undeploy_it(deploy_id, count);
  });
}

function undeploy_it(deploy_id, count) {
  console.log("undeploying " + deploy_id);
  vertx.undeployVerticle(deploy_id, function() {
    console.log("undeployed");
    count++;
    if (count < 100000) {
      deploy_it(count);
    } else {
      console.log("done!");
    }
  });
}


vertx.deployVerticle('child.js');
deploy_it(0) ;