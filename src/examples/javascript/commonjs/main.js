var Server, handler;

Server = require('./server');
handler = require('./handler');

server = new Server(8080, 'localhost');
server.use(handler);
server.start();