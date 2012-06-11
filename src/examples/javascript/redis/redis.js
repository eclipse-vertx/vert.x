load('vertx.js');

var eb = vertx.eventBus;

vertx.deployVerticle('redis-client', null, 1, function() {
	java.lang.System.out.println("redis connected");
	
	test_exists();
	eb.send('vertx.redis-client', {command: "set", key: 'name', value: 'thorsten'});
	
	test_exists();
	test_get();
	eb.send('vertx.redis-client', {command: "del", keys: ['name']});
	test_exists();
	
});



function  test_exists () {
	eb.send('vertx.redis-client', {command: "exists", key: 'name'},
	function(reply) {
		if (reply.status === 'ok') {
			console.log('exists: ' + reply.value);
		} else {
			console.error('Failed exists: ' + reply.message);
		}
    }
);
}

function  test_get () {
	eb.send('vertx.redis-client', {command: "get", key: 'name'},
	function(reply) {
		if (reply.status === 'ok') {
			console.log('value: ' + reply.value);
		} else {
			console.error('Failed get: ' + reply.message);
		}
    }
);
}