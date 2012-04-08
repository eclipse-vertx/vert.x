load('vertx.js')

var eb = vertx.eventBus;

var bridgeAddress = 'test.amqpBridge'
var handlerAddress = 'test.handler'
var rabbitTopic = 'rabbitqueue'

var conf = {
  address: bridgeAddress,
  uri: 'amqp://localhost'
};

vertx.deployWorkerVerticle('org.vertx.java.busmods.amqp.AmqpBridge', conf, 1, function() {
  stdout.println('amqp verticle is deployed')

  eb.registerHandler('test.handler', function(msg) {
    stdout.println('received msg in handler ' + msg);
  });

  var msg = {
    exchange: 'amq.topic',
    routing_key: rabbitTopic,
    forward: handlerAddress
  }
  eb.send(bridgeAddress + '.create-consumer', msg);

  // Now send some messages
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