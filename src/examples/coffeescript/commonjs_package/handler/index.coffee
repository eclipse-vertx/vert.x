class Handler
    constructor: (req) ->
        req.response.end "<html><body><h1>Hello from vert.x using Coffeescript with CommonJS packages!</h1></body></html>"        

module.exports = Handler