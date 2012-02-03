load('vertx.js')

vertx.EventBus.send('example.address', 'hello world');
