(function DemoViewModel() {

  var that = this;
  var eb = new vertx.EventBus('http://localhost:8080/eventbus');
  that.emailAddress = ko.observable('');
  that.items = ko.observableArray([]);

  eb.onopen = function() {

    // Get the static data

    eb.send('demo.persistor', {action: 'find', collection: 'albums', matcher: {} },
      function(reply) {
        if (reply.status === 'ok') {
          var albumArray = [];
          for (var i = 0; i < reply.results.length; i++) {
            albumArray[i] = new Album(reply.results[i]);
          }
          that.albums = ko.observableArray(albumArray);
          ko.applyBindings(that);
        } else {
          console.error('Failed to retrieve albums: ' + reply.message);
        }
      });
  };

  eb.onclose = function() {
    eb = null;
  };

  that.addToCart = function(album) {
    for (var i = 0; i < that.items().length; i++) {
      var compare = that.items()[i];
      if (compare.album._id === album._id) {
        compare.quantity(compare.quantity() + 1);
        return;
      }
    }
    that.items.push(new CartItem(album));
  };

  that.removeFromCart = function(cartItem) {
    that.items.remove(cartItem);
  };

  that.total = ko.computed(function() {
    var tot = 0;
    for (var i = 0; i < that.items().length; i++) {
      var item = that.items()[i];
      tot += item.quantity() * item.album.price;
    }
    tot = '$' + tot.toFixed(2);
    return tot;
  });

  that.orderReady = ko.computed(function() {
    var or =  that.items().length > 0 && that.emailAddress().trim() != '';
    return or;
  });

  that.orderSubmitted = ko.observable(false);

  that.submitOrder = function() {

    if (!orderReady()) {
      return;
    }

    var orderJson = ko.toJS(that.items);
    var order = {
      email: that.emailAddress(),
      items: orderJson
    }

    eb.send('demo.orderQueue', order, function(reply) {
      if (reply.status === 'ok') {
        //alert('Your order has been accepted, and an email has been sent');
        that.orderSubmitted(true);
        // Timeout the order confirmation box after 2 seconds
        window.setTimeout(function() { that.orderSubmitted(false); }, 2000);
      } else {
        console.error('Failed to accept order');
      }
    });
  }

  function Album(json) {
    var that = this;
    that._id = json._id.$oid;
    that.genre = json.genre;
    that.artist = json.artist;
    that.title = json.title;
    that.price = json.price;
    that.formattedPrice = ko.computed(function() {
      return '$' + that.price.toFixed(2);
    });
  }

  function CartItem(album) {
    var that = this;
    that.album = album;
    that.quantity = ko.observable(1);
  }
})();
