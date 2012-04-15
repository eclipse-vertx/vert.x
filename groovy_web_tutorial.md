In this tutorial we're going to write a real web-application using vert.x.

The application is a shop application called "vToons" which allows you to buy tracks online.

The application consists of a client-side JavaScript MVVM application which communicates with a set of server-side components via the vert.x event bus.

In this version of this tutorial we've written it all in Groovy. If you'd prefer to use Ruby, JavaScript or Java please see the version for that language. You could also mix and match - writing your components in a variety of languages.

## Step 1. Install vert.x

If you haven't yet installed vert.x, [do that now](install.html). 

The rest of the tutorial will assume you have installed vert.x in directory `VERTX_HOME`.

## Step 2. Create a Web Server

Open a console, and create a new, empty directory. `cd` into it.

The first thing we're going to need is a web server, so let's write one.

Open a text editor and copy the following into it:

    def server = vertx.createHttpServer()

    server.requestHandler { req ->
      if (req.path == '/') {
        req.response.sendFile('web/index.html')
      } else if (req.path.indexOf('..') == -1) {
        req.response.sendFile("web/${req.path}")
      } else {
        req.response.setStatusCode(404)
        req.response.end()
      }
    }


We're creating an instance of `HttpServer` and we're setting a request handler function on it. The request handler gets called every time an HTTP request arrives on the server.

If the request is for the root, we just serve `index.html`. Otherwise we serve a file from the `web` directory corresponding to the path requested.

If the path url contains the string `..` we just return a 404. This is to prevent someone reading files outside of the `web` directory.    

Save it as `WebServer.groovy`.

Now, create a directory called web with a file `index.html` in it:

    tim@Ethel:~/tutorial$ mkdir web
    tim@Ethel:~/tutorial$ echo "<html><body>Hello World</body></html>" > web/index.html
    
And run the web server:

    tim@Ethel:~/tutorial$ vertx run WebServer.groovy
    
Point your browser at `http://localhost:8080`. You should see a page returned with 'Hello World'.    

That's the web server done.
    
## Step 3. Serve the client-side app

Now we have a working web server, we need to serve the actual client side app.

For this demo, we've written it using [knockout.js](http://knockoutjs.com/) and [Twitter bootstrap](http://twitter.github.com/bootstrap/), but in your apps you can use whatever client side toolset you feel most comfortable with (e.g. jQuery, backbone.js, ember.js or whatever). Vert.x is 100% agnostic about what client side tools you use.

The purpose of this tutorial is not to show you how knockout.js or Twitter bootstrap works so we won't delve into the client app in much detail.

Copy the client side application from the vert.x installation into our web directory as follows:  
    
    tim@Ethel:~/tutorial$ cp -r $VERTX_HOME/examples/groovy/webapp/web/* web
    
Open the file `web/js/client_app.js` in your text editor, and edit the line:

    var eb = new vertx.EventBus('https://localhost:8080/eventbus');
    
So it reads:

    var eb = new vertx.EventBus('http://localhost:8080/eventbus');      
          
        
Now, refresh your browser. The client application should now be served.

Of course, it won't do anything useful yet, since we haven't connected it up to anything, but you should at least see the layout. It should look like this: 


![Client Application](tutorial_1.png)


Take some time to click around the app. It's pretty self explanatory.

In the centre there's a set of tabs which let you flick between the shop, and your cart.

On the left hand bar there's a form which allows you to login.    

### Step 4. Get the Persistor up and running

Vert.x ships with an out of the box bus module (busmod) called `MongoPersistor`. A busmod is a component which communicates with other components on the vert.x event bus by exchanging JSON messages.

The `MongoPersistor` busmod allows you to store/update/delete/find data in a MongoDB database. (For detailed info on it, please see the busmods manual).

We're going to use a persistor in our application for a few different things:

* Storing the catalogue of track data.
* Storing usernames and passwords of users
* Storing orders

You could start a persistor on the command line by calling `vertx run org.vertx.java.busmods.persistor.MongoPersistor` but we're going to need to start several components to form our application, so it makes sense to create a controlling verticle (A verticle is just the name we give to any vert.x component) that starts up all the other components for us.

It can also contain the JSON configuration for our application. All verticles can be configured using JSON.

Open a text editor and copy in the following:

    // Our application config

    
    def persistorConf = [
      address: 'demo.persistor',
      db_name: 'test_db'
    ]
    
    container.with {

      // Deploy the busmods

      deployWorkerVerticle('org.vertx.java.busmods.persistor.MongoPersistor', persistorConf)

      // Start the web server

      deployVerticle('WebServer.groovy')
    }
    

Save it as `App.groovy`.

The calls to `deployVerticle` and `deployWorkerVerticle` are a programmatic way of starting other verticles from inside the code of a verticle.

As you can see, the persistor needs some configuration and that is passed in when we deploy the persistor verticle. The configuration is a a JSON object (expressed as a Map)

The persistor needs two pieces of information:

* The address that it will listen to on the event bus for incoming messages.

* The name of the database.

Of course you'll also need to make sure you have installed a MongoDB instance on the local machine, with default settings.

Now CTRL-C the web server you started earlier and run `App.groovy` with 

    tim@Ethel:~/tutorial$ vertx run App.groovy 
    
The persistor and web server should be running and it should serve the client application as before.

## Step 5. Connecting up the client side to the Event Bus

So far we have a web server running, and a server side persistor listening on the event bus, but not doing anything.

We need to connect up the client side so it can interact with the persistor on the event bus.

To that we use a SockJS bridge.

SockJS is a technology which allows a full-duplex WebSocket-like connection between browsers and servers, even if the browser or network doesn't support websockets.

The SockJS bridge is a server side vert.x component which uses SockJS to connect up the browser with the vert.x event bus on the server side.

SockJS and the SockJS bridge is explained in detail in the documentation, so we won't go into more detail here.

To create a SockJS bridge, we first call the 'createSockJSServer()' method on our vertx instance to create the SockJS server and then we call the `bridge` method on it to create the bridge between SockJS and the event bus.

    vertx.createSockJSServer(server).bridge(['prefix': '/eventbus'], [] )

Edit `WebServer.groovy` so it looks like:

    def server = vertx.createHttpServer()

    server.requestHandler { req ->
      if (req.path == '/') {
        req.response.sendFile('web/index.html')
      } else if (req.path.indexOf('..') == -1) {
        req.response.sendFile("web/${req.path}")
      } else {
        req.response.setStatusCode(404)
        req.response.end()
      }
    }

    vertx.createSockJSServer(server).bridge(prefix: '/eventbus', [])

    server.listen(8080, 'localhost')


What we're doing here is creating an instance of a SockJS server and telling it that any requests it receives with the prefix `/eventbus` should be considered traffic for the event bus.

The original request handler for the static resources is still there, and that will still be invoked for any requests that don't have the prefix `/eventbus` on their path.

There's one other thing we have to do here.

For security reasons, by default, the SockJS bridge will reject all event bus messages sent from the client side. After all, we don't want just anyone being able to delete everything in the database.

To allow messages through we have to tell the bridge what sort of messages we're going to allow through. This is done by specifying permitted matches, using the second parameter when creating the bridge.

Initially, we only want to allow through requests to the persistor to find albums. This will be used by the client side application to request the catalogue so it can display the list of available items to buy.

Edit the code in `WebServer.groovy` so it looks like:

    def server = vertx.createHttpServer()

    server.requestHandler { req ->
      if (req.path == '/') {
        req.response.sendFile('web/index.html')
      } else if (req.path.indexOf('..') == -1) {
        req.response.sendFile("web/${req.path}")
      } else {
        req.response.setStatusCode(404)
        req.response.end()
      }
    }

    vertx.createSockJSServer(server).bridge(prefix: '/eventbus',
      [
        // Allow calls to get static album data from the persistor
        [
          'address': 'demo.persistor',
          'match': [
            'action': 'find',
            'collection': 'albums'
          ]
        ]
      ])

    server.listen(8080, 'localhost')

    
The second parameter to the bridge method is a list of matches which determine which messages we're going to let through.
    
In our case, we're going to allow through any event bus messages from the client side to the address `demo.persistor` (which is where the persistor is listening), where the action field has the value `find`, and the `collection` field has the value `albums`.

Save the file.

## Step 6. Inserting the Static Data

We're almost at the point where the client side app can see the catalogue data. But first we need to insert some static data.

To do this we will create a script called `StaticData.groovy` which just inserts catalogue and other data needed by the application in the database. It does this by sending JSON messages on the event bus.

Copy `StaticData.groovy` into your directory as follows:

    tim@Ethel:~/tutorial$ cp $VERTX_HOME/examples/groovy/webapp/StaticData.groovy .

We want to insert the static data only after the persistor verticle has completed starting up so we edit `App.groovy` as follows:

    deployWorkerVerticle('org.vertx.java.busmods.persistor.MongoPersistor', appConf['persistor_conf'], 1, {
        deployVerticle('StaticData.groovy')
    })
    
The block that we're specifying in the call to `deployWorkerVerticle` will be invoked when the persistor is fully started. In that block we just load the static data script.

Save the edited `App.groovy` and restart it.

    vertx run App.groovy
    
Refresh your browser.

You should now see the catalogue displayed in the client side app:

![Client Application](tutorial_2.png)  

Now there is some stuff to buy, you should be able to add stuff to your cart, and view the contents of your cart by clicking on the cart tab.

## Step 7. Requesting data from the server

As previously mentioned, this isn't a tutorial on how to write a knockout.js client-side application, but let's take a quick look at the code in the client side app that requests the catalogue data and populates the shop.

The client side application JavaScript is contained in the file `web/js/client_app.js`. If you open this in your text editor you will see the following line, towards the top of the script:

    var eb = new vertx.EventBus('http://localhost:8080/eventbus');
    
This is using the `vertxbus.js` library to create an `EventBus` object. This object is then used to send and receive messages from the event bus.

If you look a little further down the script, you will find the part which loads the catalogue data from the server and renders it:

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
  }; 
  
The `onopen` is called when, unsurprisingly, the event bus connection is fully setup and open.  

At that point we are calling the `send` function on the event bus to a send a JSON message to the address `demo.persistor`. This is the address of the MongoDB persistor busmod that we configured earlier.

The JSON message that we're sending specifies that we want to find and return all albums in the database. (For a full description of the operations that the MongoDBPersistor busmod expects you can consult the busmods manual).

The final argument that we pass to to `send` is a reply handler. This is a function that gets called when the persistor has processed the operation and sent the reply back here. The first argument to the reply handler is the reply itself.

In this case, the reply contains a JSON message with a field `results` which contains a JSON array containing the albums.

Once we get the albums we give them to knockout.js to render on the view.

## Step 8. Handling Login

In order to actually send an order, you need to be logged in so we know who has placed the order.

Vert.x ships with an out of the box busmod called `AuthManager`. This is a very simple authentication manager which sits on the event bus and provides a couple of services:

* Login. This receives a username and password, validates it in the database, and if it is ok, a session is created and the session id sent back in the reply message.

* Validate. This validates a session id, returning whether it is valid or not.

For detailed information on this component please consult the busmods manual.

We're going to add an authentication manager component to our application so the user can login.

Open up `App.groovy` again, and add the following line immediately after the deployment of the mongo persistor:

    deployVerticle('org.vertx.java.busmods.auth.AuthManager', appConf['auth_mgr_conf'])

Also add the following:

  def authMgrConf = [
    address: 'demo.authMgr',
    user_collection: 'users',
    persistor_address: 'demo.persistor'
  ]
      
So, App.groovy should now look like this:

    // Our application config

    
    def persistorConf = [
      address: 'demo.persistor',
      db_name: 'test_db'
    ]
    def authMgrConf = [
      address: 'demo.authMgr',
      user_collection: 'users',
      persistor_address: 'demo.persistor'
    ]      

    container.with {

      // Deploy the busmods

      deployWorkerVerticle('org.vertx.java.busmods.persistor.MongoPersistor', persistorConf, 1, {
        deployVerticle('StaticData.groovy')
      })
      deployVerticle('org.vertx.java.busmods.auth.AuthManager', authMgrConf)
      
      // Start the web server

      deployVerticle('WebServer.groovy')
    }

    
We also need to tell the SockJS bridge to expect login messages coming onto the event bus.

Edit `WebServer.groovy` and add the following match to the list of matches passed into the `bridge` method:

    // Allow user to login
    [
      'address' : 'demo.authMgr.login'
    ]
    
So the line that constructs the SockJSBridge looks like:

    vertx.createSockJSServer(server).bridge(prefix: '/eventbus',
      [
        // Allow calls to get static album data from the persistor
        [
          'address' : 'demo.persistor',
          'match' : [
            'action' : 'find',
            'collection' : 'albums'
          ]
        ],
        // Allow user to login
        [
          'address' : 'demo.authMgr.login'
        ]
      ]
    )

Now restart the application

    vertx run App.groovy
    
And refresh your browser.

Attempt to log-in with username `tim` and password `password`. A message should appear on the left telling you you are logged in!.

![Client Application](tutorial_3.png)

Let's take a look at the client side code which does the login.

Open `web/js/client_app.js` and scroll down to the `login` function. This gets trigged by knockout when the login button is pressed on the page.

    eb.send('demo.authMgr.login', {username: that.username(), password: that.password()}, function (reply) {
        if (reply.status === 'ok') {
          that.sessionID(reply.sessionID);
        } else {
          alert('invalid login');
        }
      });
      
As you can see, it sends a login JSON message to the authentication manager busmod with the username and password.

When the reply comes back with status `ok`, it stores the session id which causes knockout to display the "Logged in as... " message.

It's as easy as that.

## Step 9. Processing Orders

The next part to implement is processing of orders.

One naive way to do this would be to directly insert the order in the database by sending a message to the MongoDB persistor, then sending another message to the mailer to send an order confirmation email.

Problem is we don't want to just anyone inserting data into the database or sending emails from the client side (we don't want to become a spam relay!).

A better solution is to write a simple order manager verticle which sits on the event bus on the server and handles the whole order processing for us.

As orders arrive we want to

1. Validate the user is logged in
2. If ok, then persist the order in the database
3. If ok, then send back a confirmation to the client side.

Copy the following into your editor and save it as `OrderMgr.groovy` in your tutorial top-level directory.

    eb = vertx.eventBus

    eb.registerHandler('demo.orderMgr') { message ->
      validateUser(message)
    }

    def validateUser(message) {
      eb.send('demo.authMgr.validate', [ sessionID: message.body['sessionID'] ]) { reply ->
        if (reply.body['status'] == 'ok') {
          message.body['username'] = reply.body['username']
          saveOrder(message)
        } else {
          println "Failed to validate user"
        }
      }
    }

    def saveOrder(message) {
      eb.send('demo.persistor',
          [action: 'save', collection: 'orders', document: message.body]) { reply ->
        if (reply.body['status'] == 'ok') {
          println "order successfully processed!"
          message.reply([status: 'ok']) // Reply to the front end
        } else {
          println "Failed to save order"
        }
      }
    }
    
The order manager verticle registers a handler on the address `demo.orderMgr`. When any message arrives on the event bus a `Message` object is passed to the handler.

The actual order is in the `body` attribute of the message. When an order message arrives the first thing it does is sends a message to the authentication manager to validate if the user is logged in, given their session id (which is passed in the order message).

If the user was logged in ok, the order is then persisted using the MongoDB persistor. If that returns ok, we send back a message to the client.

All messages have a `reply` function which can be invoked to send back a reply to the sender of the message. In other words, it's an implementation of the *request-response* pattern.

We'll also need to add another accepted match on the SockJSBridge config in `WebServer.groovy` to tell it to let through orders:
    
    // Let through orders posted to the order manager
    [
      'address' : 'demo.orderMgr'
    ]
    
So, it should look like:

    vertx.createSockJSServer(server).bridge(prefix: '/eventbus',
      [
        // Allow calls to get static album data from the persistor
        [
          'address' : 'demo.persistor',
          'match' : [
            'action' : 'find',
            'collection' : 'albums'
          ]
        ],
        // Allow user to login
        [
          'address' : 'demo.authMgr.login'
        ],
        // Let through orders posted to the order manager
        [
          'address' : 'demo.orderMgr'
        ]
      ]
    )
    
We'll also have to add a line to `App.groovy` to load the `OrderMgr.groovy` verticle, just before the web server is started:

    // Start the order manager

    deployVerticle('OrderMgr.groovy')

Ok, let's take a look at the client side code which sends the order.

Open up `web/js/client_app.js` again, and look for the function `submitOrder`.

    that.submitOrder = function() {

        if (!orderReady()) {
          return;
        }

        var orderJson = ko.toJS(that.items);
        var order = {
          sessionID: that.sessionID(),
          items: orderJson
        }

        eb.send('demo.orderMgr', order, function(reply) {
          if (reply.status === 'ok') {
            that.orderSubmitted(true);
            // Timeout the order confirmation box after 2 seconds
            window.setTimeout(function() { that.orderSubmitted(false); }, 2000);
          } else {
            console.error('Failed to accept order');
          }
        });
      }; 
    };    
    
This function simply converts the order into a JSON object, then calls `send` on the event bus to send it to the order manager verticle that we registered on address `demo.orderMgr`.

When the reply comes back we tell knockout to display a message.

Everything should be in order, so restart the app again:
    
    vertx run App.groovy
    
Refresh the browser.

Now log-in and add a few items into your cart. Click on the cart tab and click "Submit Order". The message "Your order has been accepted, an email will be on your way to you shortly" should appear!

Take a look in the console window of the application. You should see the order has been logged.

![Client Application](tutorial_4.png)

** Congratulations! You have just placed an order. **

## Step 11. Securing the Connection

So far in this tutorial, all client-server traffic has been over an unsecured socket. That's not a very good idea since we've been sending login credentials and orders.

Configuring vert.x to use secure sockets is very easy. (For detailed information on configuring HTTPS, please
see the manual).

Edit `WebServer.groovy` again, and edit the line that creates the HTTP server so it reads:

    def server = vertx.createHttpServer(SSL: true,
                                        keyStorePath: 'server-keystore.jks',
                                        keyStorePassword: 'wibble')
        
Copy the keystore from the distribution

    tim@Ethel:~/tutorial$ cp $VERTX_HOME/examples/groovy/webapp/server-keystore.jks . 
    
*The keystore is just a Java keystore which contains the certificate for the server. It can be manipulated using the Java `keytool` command.*           
        
You'll also need to edit `web/js/client_app.js` so the line which creates the client side event bus instance now uses `https` as the protocol:

    var eb = new vertx.EventBus('https://localhost:8080/eventbus');
    
Now restart the app again.

    vertx run App.groovy
    
And go to your browser. This time point your browser at `https://localhost:8080`. *Note it is **https** not http*.

*You'll initially get a warning from your browser saying the server certificate is unknown. This is to be expected since we haven't told the browser to trust it. You can ignore that for now. On a real server your server cert would probably be from a trusted certificate authority.*

Now login, and place an order as before.

Easy peasy. **It just works**

## Step 12. Scaling the application

### Scaling the web server

Scaling up the web server part is trivial. Simply start up more instances of the webserver. You can do this by changing the line that starts the verticle `WebServer.groovy` to something like:

    // Start 32 instances of the web server!

    deployVerticle('WebServer.groovy', null, 32)
    
(*Vert.x is clever here, it notices that you are trying to start multiple servers on the same host and port, and internally it maintains a single listening server, but round robins connections between the various instances*.)

### Scaling the processing.

In our trivial example it probably won't make much difference, but if you have some fairly intensive processing that needs to be done on the orders, it might make sense to maintain a farm of order processors, and as orders come into the order manager, to farm them out to one of the available processors.

You can then spread the processing load not just between multiple processors on the same machine, but between many processors on different machines of the network.

Doing this is easy with vert.x. Vert.x ships with an out-of-the-box busmod called `WorkQueue` which allows you to easily create queues of work can be shared out amongst many processors.

Please consult the busmods manual for more information on this.

## Final Thoughts

This tutorial gives you just a taste of the kinds of things you can do with vert.x. 

With just a couple of handfuls of code you have created a real, scalable web-app.

*Copies of this document may be made for your own use and for distribution to others, provided that you do not charge any fee for such copies and further provided that each copy contains this Copyright Notice, whether distributed in print or electronically.*


       
        


          

    

    


        





    
    




    


