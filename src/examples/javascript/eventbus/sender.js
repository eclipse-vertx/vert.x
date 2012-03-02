load('vertx.js')

var eb = vertx.EventBus;

var address = 'example.address'

eb.send(address, 'hello world');
