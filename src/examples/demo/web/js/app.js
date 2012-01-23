
// This is a simple *viewmodel* - JavaScript that defines the data and behavior of your UI
function AppViewModel() {
  var that = this;

  function PopulateItems() {
    return [new LineItem(12, 'Indie Rock', 'Muse', 'Black Holes and Revelations', '7.95'),
            new LineItem(345, 'Wub wub', 'Skrillex', 'Scary Monsters and Nice Sprites', '7.95'),
            new LineItem(28, 'Hip hop', 'NWA', 'Straight Outta Compton', '6.50'),
            new LineItem(1233, 'Easy listening', 'Ena Baga', "The Happy Hammond", '0.50')];
  };

  that.items = ko.observableArray(PopulateItems());

  that.cart = new Cart();

  that.addToCart = function(lineItem) {
    console.log('Adding item ' + lineItem.artist);
    that.cart.items.push(lineItem);
  };

  var eb = new vertx.EventBus("http://localhost:8080/eventbus");

  eb.onopen = function() {
    // Activates knockout.js
    ko.applyBindings(that);
  };

  eb.onclose = function() {
    console.log('Not connected');
    eb = null;
  };

  function Cart() {
    var that = this;
    that.items = ko.observableArray([]);

    that.submitOrder = function() {
      eb.send('demo.orderManager', {blah:'wibble'}, function(reply) {
        console.log('Order has been received: ' + JSON.stringify(reply));
      });
    }
  }

  function LineItem(itemID, genre, artist, album, price) {
    var that = this;
    that.itemID = itemID;
    that.genre = genre;
    that.artist = artist;
    that.album = album;
    that.price = price;
  }
}

new AppViewModel();

