load('vertx.js');

var eb = vertx.eventBus;

vertx.deployVerticle('redis-client', null, 1, function() {
	java.lang.System.out.println("redis connected");
	
	eb.send('vertx.redis-client', {command: "set", key: 'counter', value: '1'});
	test_get();
	test_incr(1);
	test_get();
	test_decr(2);
	test_get();
});



function test_get () {
	eb.send('vertx.redis-client', {command: "get", key: 'counter'},
	function(reply) {
		if (reply.status === 'ok') {
			console.log('exists: ' + reply.value);
		} else {
			console.error('Failed exists: ' + reply.message);
		}
    }
	);
}
function test_incr (oldvalue) {
	eb.send('vertx.redis-client', {command: "incr", key: 'counter'},
	function(reply) {
		console.log("result of incr");
		if (reply.status === 'ok') {
			console.log('value: ' + reply.value + " (" + oldvalue + ")");
		} else {
			console.error('Failed exists: ' + reply.message);
		}
    }
	);
}
function test_decr (oldvalue) {
	eb.send('vertx.redis-client', {command: "decr", key: 'counter'},
	function(reply) {
		console.log("result of decr");
		if (reply.status === 'ok') {
			console.log('value: ' + reply.value + " (" + oldvalue + ")");
		} else {
			console.error('Failed exists: ' + reply.message);
		}
    }
	);
}