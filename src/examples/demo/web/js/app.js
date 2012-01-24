(function DemoViewModel() {

  var that = this;
  that.cart = new Cart();
  var eb = new vertx.EventBus('http://localhost:8080/eventbus');

  eb.onopen = function() {

    // Get the static data

    eb.send('demo.persistor', {action: 'find', collection: 'albums', matcher: {} },
      function(reply) {
        if (reply.status === 'ok') {
          that.items = ko.observableArray(reply.results);

          ko.applyBindings(that);
        } else {
          console.error('Failed to retrieve albums: ' + reply.message);
        }
      });
  };

  eb.onclose = function() {
    eb = null;
  };

  function Cart() {
    var that = this;

    that.emailAddress = ko.observable('');
    that.items = ko.observableArray([]);

    that.submitOrder = function() {

      var orderJson = ko.toJS(that.items);
      var order = {
        email: that.emailAddress(),
        items: orderJson
      }

      eb.send('demo.orderQueue', order, function(reply) {
        if (reply.status === 'ok') {
          console.log('Order has been received for processing');
        } else {
          console.error('Failed to accept order');
        }
      });
    }

    that.addToCart = function(album) {
      that.items.push(album);
    };
  }
})();
