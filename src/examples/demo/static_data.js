load('vertx.js');

var eb = vertx.EventBus;

var pa = 'demo.persistor';

var albums = [
  {
    artist: 'Muse',
    genre: 'Indie Rock',
    album: 'Black Holes and Revelations',
    price: 5.95
  },
  {
    artist: 'NWA',
    genre: 'Hip Hop',
    album: 'Straight Outta Compton',
    price: 6.95
  },
  {
    artist: 'Ena Baga',
    genre: 'Easy Listening',
    album: 'The Happy Hammond',
    price: 0.50
  },
  {
    artist: 'Skrillex',
    genre: 'Wub wub wub',
    album: 'Scary Monsters and Nice Sprites',
    price: 4.50
  }
];

// First delete everything

eb.send(pa, {action: 'delete', collection: 'albums', matcher: {}});

// Insert albums - in real life price would probably be stored in a different collection, but, hey, this is a demo.

for (var i = 0; i < albums.length; i++) {
  eb.send(pa, {
    action: 'save',
    collection: 'albums',
    document: albums[i]
  });
}