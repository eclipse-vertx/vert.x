load('vertx.js');

var eb = vertx.eventBus;

var pa = 'vertx.mongopersistor';

var albums = [
  {
    artist: 'The Wurzels',
    genre: 'Scrumpy and Western',
    title: 'I Am A Cider Drinker',
    price: 0.99
  },
  {
    artist: 'Vanilla Ice',
    genre: 'Hip Hop',
    title: 'Ice Ice Baby',
    price: 0.01
  },
  {
    artist: 'Ena Baga',
    genre: 'Easy Listening',
    title: 'The Happy Hammond',
    price: 0.50
  },
  {
    artist: 'The Tweets',
    genre: 'Bird related songs',
    title: 'The Birdy Song',
    price: 1.20
  }
];

// First delete everything

eb.send(pa, {action: 'delete', collection: 'albums', matcher: {}});

eb.send(pa, {action: 'delete', collection: 'users', matcher: {}});

// Insert albums - in real life price would probably be stored in a different collection, but, hey, this is a demo.


for (var i = 0; i < albums.length; i++) {
  eb.send(pa, {
    action: 'save',
    collection: 'albums',
    document: albums[i]
  });
}

// And a user

eb.send(pa, {
  action: 'save',
  collection: 'users',
  document: {
    firstname: 'Tim',
    lastname: 'Fox',
    email: 'tim@localhost.com',
    username: 'tim',
    password: 'password'
  }
});
