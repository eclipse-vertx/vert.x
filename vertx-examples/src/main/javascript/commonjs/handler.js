module.exports = function(req) {
  req.response.end("<html><body><h1>Hello from vert.x using CommonJS!</h1></body></html>");
};