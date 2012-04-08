load('vertx.js')

/*
To run:
1) Make sure you have a Rabbit server running on localhost
2) `vertx run amqp.js` from this directory
 */

var eb = vertx.eventBus;

var bridgeAddress = 'test.amqpBridge'
var handlerAddress = 'test.handler'
var rabbitTopic = 'rabbitqueue'

var conf = {
  address: bridgeAddress, // This is the address at which the bridge will listen for incoming messages
  uri: 'amqp://localhost' // This is the uri of the Rabbit server
};

// First deploy the amqp bridge
vertx.deployWorkerVerticle('org.vertx.java.busmods.amqp.AmqpBridge', conf, 1, function() {

  // Bridge is deployed

  // Now register a handler - this handler will receive any messages sent from the AMQP topic
  eb.registerHandler('test.handler', function(msg) {
    stdout.println('received msg in handler ' + msg);
  });

  // Tell the AMQP bridge to create a consumer on the specified exchange and routing key and forward those messages
  // to our handler
  var msg = {
    exchange: 'amq.topic',
    routing_key: rabbitTopic,
    forward: handlerAddress
  }
  eb.send(bridgeAddress + '.create-consumer', msg);

  // Now send some messages  - they should appear in our handler
  for (var i = 0; i < 10; i++) {
    var msg = {
      exchange: 'amq.topic',
      routing_key: rabbitTopic,
      body: 'message' + i
    }
    eb.send(bridgeAddress + '.send', msg)
    stdout.println('sent message')
  }
});