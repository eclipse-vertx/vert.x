(function DemoViewModel() {

  var that = this;
  that.cart = new Cart();
  var eb = new vertx.EventBus('http://localhost:8080/eventbus');

  eb.onopen = function() {

    // Get the static data

    eb.send('demo.persistor', {action: 'find', collection: 'albums', matcher: {} },
      function(reply) {
        if (reply.status === 'ok') {
          var albumArray = [];
          for (var i = 0; i < reply.results.length; i++) {
            albumArray[i] = new Album(reply.results[i]);
          }
          that.items = ko.observableArray(albumArray);
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
          alert('Your order has been accepted, and an email has been sent');
        } else {
          console.error('Failed to accept order');
        }
      });
    }

    that.addToCart = function(album) {
      console.log("in addto cart");
      that.items.push(album);
    };

    that.canSubmit = ko.computed(function() {
      console.log("in cansubmit, items.length: " + that.items.length + " trimmed " + that.emailAddress().trim());
      return that.items().length > 0 && that.emailAddress().trim() != '';
    });
  }

  function Album(json) {
    var that = this;
    that.genre = json.genre;
    that.artist = json.artist;
    that.album = json.album;
    that.price = json.price;
    that.formattedPrice = ko.computed(function() {
      return '£' + that.price.toFixed(2);
    });
  }
})();
