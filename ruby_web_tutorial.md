In this tutorial we're going to write a real web-application using vert.x.

The application is a shop application called "vToons" which allows you to buy tracks online.

The application consists of a client-side JavaScript MVVM application which communicates with a set of server-side components via the vert.x event bus.

In this version of this tutorial we've written it all in Ruby. If you'd prefer to use JavaScript or Java please see the version for that language. You could also mix and match - writing your components in a variety of languages.

If you'd rather just look at the code than work through the tutorial, the complete working example is present in the `webapp` directory of the examples in the distribution. Read the README there for instructions on how to run it.

## Step 1. Install vert.x

If you haven't yet installed vert.x, [do that now](install.html). 

The rest of the tutorial will assume you have installed vert.x in directory `VERTX_HOME`.

## Step 2. Create a Web Server

Open a console, and create a new, empty directory. `cd` into it.

The first thing we're going to need is a web server, so let's write one.

Open a text editor and copy the following into it:

    require('vertx')
    include Vertx

    @server = HttpServer.new

    @server.request_handler do |req|
      if req.path == '/'
        req.response.send_file('web/index.html')
      elsif !req.path.include?('..')
        req.response.send_file('web' + req.path)
      else
        req.response.status_code = 404
        req.response.end
      end
    end.listen(8080, 'localhost')


We're creating an instance of `HttpServer` and we're setting a request handler function on it. The request handler gets called every time an HTTP request arrives on the server.

If the request is for the root, we just serve `index.html`. Otherwise we serve a file from the `web` directory corresponding to the path requested.

If the path url contains the string `..` we just return a 404. This is to prevent someone reading files outside of the `web` directory.    

Save it as `web-server.rb`.

Now, create a directory called web with a file `index.html` in it:

    tim@Ethel:~/tutorial$ mkdir web
    tim@Ethel:~/tutorial$ echo "<html><body>Hello World</body></html>" > web/index.html
    
And run the web server:

    tim@Ethel:~/tutorial$ vertx run web_server.rb
    
Point your browser at `http://localhost:8080`. You should see a page returned with 'Hello World'.    

That's the web server done.
    
## Step 3. Serve the client-side app

Now we have a working web server, we need to serve the actual client side app.

For this demo, we've written it using [knockout.js](http://knockoutjs.com/) and [Twitter bootstrap](http://twitter.github.com/bootstrap/), but in your apps you can use whatever client side toolset you feel most comfortable with (e.g. jQuery, backbone.js, ember.js or whatever).

The purpose of this tutorial is not to show you how knockout.js or Twitter bootstrap works so we won't delve into the client app in much detail.

Copy the client side application from the vert.x installation into our web directory as follows:  
    
    tim@Ethel:~/tutorial$ cp -r $VERTX_HOME/examples/ruby/webapp/web/* web
    
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

Vert.x ships with an out of the box bus module (busmod) called `mongo-persistor`. A busmod is a component which communicates with other components on the vert.x event bus by exchanging JSON messages.

The `mongo-persistor` busmod allows you to store/update/delete/find data in a MongoDB database. (For detailed info on it, please see the modules manual).

We're going to use a persistor in our application for a few different things:

* Storing the catalogue of track data.
* Storing usernames and passwords of users
* Storing orders

You could start a persistor on the command line by calling `vertx run mongo-persistor` but we're going to need to start several components to form our application, so it makes sense to create a controlling verticle (A verticle is just the name we give to any vert.x component) that starts up all the other components for us.

It can also contain the JSON configuration for our application. All verticles can be configured using JSON.

Open a text editor and copy in the following:
    
    require('vertx')

    # Our application config

    persistor_conf = {
      'address' => 'demo.persistor',
      'db_name' => 'test_db'
    }

    # Deploy the busmods

    Vertx.deploy_verticle('mongo-persistor', persistor_conf);

    # Start the web server

    Vertx.deploy_verticle('web_server.rb')  

Save it as `app.rb`.

The calls to `Vertx.deploy_verticle` are a programmatic way of starting other verticles from inside the code of a verticle.

As you can see, the persistor needs some configuration and that is passed in when we deploy the persistor verticle. The configuration is expressed in JSON.

The persistor needs two pieces of information:

* The address that it will listen to on the event bus for incoming messages.

* The name of the database.

Of course you'll also need to make sure you have installed a MongoDB instance on the local machine, with default settings.

Now CTRL-C the web server you started earlier and run `app.rb` with 

    tim@Ethel:~/tutorial$ vertx run app.rb 
    
The persistor and web server should be running and it should serve the client application as before.

## Step 5. Connecting up the client side to the Event Bus

So far we have a web server running, and a server side persistor listening on the event bus, but not doing anything.

We need to connect up the client side so it can interact with the persistor on the event bus.

To that we use a SockJS bridge.

SockJS is a technology which allows a full-duplex WebSocket-like connection between browsers and servers, even if the browser or network doesn't support websockets.

The SockJS bridge is a server side vert.x component which uses SockJS to connect up the browser with the vert.x event bus on the server side.

SockJS and the SockJS bridge is explained in detail in the documentation, so we won't go into more detail here.

To create a SockJS bridge, we just create an instance of `Vertx::SockJSServer` and call the `bridge` method on it as follows:

    Vertx::SockJSServer.new(server).bridge({'prefix' => '/eventbus'}, [] )

Edit `web_server.rb` so it looks like:

    require('vertx')
    include Vertx

    @server = HttpServer.new

    @server.request_handler do |req|
      if req.path == '/'
        req.response.send_file('web/index.html')
      elsif !req.path.include?('..')
        req.response.send_file('web' + req.path)
      else
        req.response.status_code = 404
        req.response.end
      end
    end

    # Link up the client side to the server side event bus
    Vertx::SockJSServer.new(@server).bridge({'prefix' => '/eventbus'}, [])

    @server.listen(8080, 'localhost')


What we're doing here is creating an instance of a SockJS server and telling it that any requests it receives with the prefix `/eventbus` should be considered traffic for the event bus.

The original request handler for the static resources is still there, and that will still be invoked for any requests that don't have the prefix `/eventbus` on their path.

There's one other thing we have to do here.

For security reasons, by default, the SockJS bridge will reject all event bus messages sent from the client side. After all, we don't want just anyone being able to delete everything in the database.

To allow messages through we have to tell the bridge what sort of messages we're going to allow through. This is done by specifying permitted matches, using the second parameter when creating the bridge.

Initially, we only want to allow through requests to the persistor to find albums. This will be used by the client side application to request the catalogue so it can display the list of available items to buy.

Edit the code in `web_server.rb` so it looks like:

    require('vertx')
    include Vertx

    @server = HttpServer.new
    
    @server.request_handler do |req|
      if req.path == '/'
        req.response.send_file('web/index.html')
      elsif !req.path.include?('..')
        req.response.send_file('web' + req.path)
      else
        req.response.status_code = 404
        req.response.end
      end
    end

    # Link up the client side to the server side event bus
    Vertx::SockJSServer.new(@server).bridge({'prefix' => '/eventbus'},
     [
        # Allow calls to get static album data from the persistor
        {
          'address' => 'demo.persistor',
          'match' => {
            'action' => 'find',
            'collection' => 'albums'
          }
        }
      ])

    @server.listen(8080, 'localhost')

    
The second parameter to the `bridge` method is an array of matches which determine which messages we're going to let through.
    
In our case, we're going to allow through any event bus messages from the client side to the address `demo.persistor` (which is where the persistor is listening), where the action field has the value `find`, and the `collection` field has the value `albums`.

Save the file.

## Step 6. Inserting the Static Data

We're almost at the point where the client side app can see the catalogue data. But first we need to insert some static data.

To do this we will create a script called `static_data.js` which just inserts catalogue and other data needed by the application in the database. It does this by sending JSON messages on the event bus.

Copy `static_data.rb` into your directory as follows:

    tim@Ethel:~/tutorial$ cp $VERTX_HOME/examples/ruby/webapp/static_data.rb .

We want to insert the static data only after the persistor verticle has completed starting up so we edit `app.rb` as follows:

    Vertx.deploy_verticle('mongo-persistor, persistor_conf) do
        load('static_data.rb')
    end
    
The block that we're specifying in the call to `deploy_verticle` will be invoked when the persistor is fully started. In that block we just load the static data script.

Save the edited `app.rb` and restart it.

    vertx run app.rb
    
Refresh your browser.

You should now see the catalogue displayed in the client side app:

![Client Application](tutorial_2.png)  

Now there is some stuff to buy, you should be able to add stuff to your cart, and view the contents of your cart by clicking on the cart tab.

## Step 7. Requesting data from the server

As previously mentioned, this isn't a tutorial on how to write a knockout.js client-side application, but let's take a look at the code in the client side app that requests the catalogue data and populates the shop.

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

In order to actually send an order, you need to be logged in.

<<<<<<< HEAD
To handle login we will start an instance of the out-of-the-box `auth-mgr` component. This is a simple busmod which handles simple user/password authentication and authorisation. Users credentials are stored in the MongoDB database. Fore more sophisticated auth, you can easily write your own auth busmod and the bridge can talk to that instead.

To login, the client sends a message on the event bus to the address `vertx.basicauthmanager.login` with fields `username` and `credentials`, and if successful it replies with a message containing a unique session id, in the `sessionID` field.

This session id should then be sent in any subsequent message from the client to the server that requires authentication (e.g. persisting an order).

When the bridge receives a message with a `sessionID` field in it, it will contact the auth manager to see if the session is authorised for that resource.

Let's add a line to start the `auth-mgr`:

Edit `app.rb` and add the following, just after where the Mongo Persistor is deployed.

    # Deploy an auth manager to handle the authentication

    Vertx.deploy_verticle('auth-mgr')
    
We'll also need to tell the bridge to let through any login messages:

    'permitted' => [
      # Allow calls to login
      {
        'address' => 'vertx.basicauthmanager.login'
      },   
      ...
       
Save, and restart the app.
=======
Vert.x ships with an out of the box busmod called `auth-mgr`. This is a very simple authentication manager which sits on the event bus and provides a couple of services:

* Login. This receives a username and password, validates it in the database, and if it is ok, a session is created and the session id sent back in the reply message.

* Validate. This validates a session id, returning whether it is valid or not.

For detailed information on this component please consult the busmods manual.

We're going to add an authentication manager component to our application so the user can login.

Open up app.rb again, and add the following line:

    Vertx.deploy_verticle('auth-mgr', auth_mgr_conf)

Also add the following to the app.rb:

    auth_mgr_conf = {
      'address' => 'demo.authMgr',
      'user_collection' => 'users',
      'persistor_address' => 'demo.persistor'
    }
      
So, app.rb should now look like this:

    require('vertx')

    # Our application config

    persistor_conf = {
      'address' => 'demo.persistor',
      'db_name' => 'test_db'
    }
    auth_mgr_conf = {
      'address' => 'demo.authMgr',
      'user_collection' => 'users',
      'persistor_address' => 'demo.persistor'
    }

    # Deploy the busmods

    Vertx.deploy_verticle('mongo-persistor', persistor_conf) do
        load('static_data.rb')
    end

    Vertx.deploy_verticle('auth-mgr', auth_mgr_conf)

    # Start the web server

    Vertx.deploy_verticle('web_server.rb') 
    
We also need to tell the SockJS bridge to expect login messages coming onto the event bus.

Edit `web_server.rb` and add the following match to the array of matches passed into the `bridge` method:

    # Allow user to login
    {
      'address' => 'demo.authMgr.login'
    }
    
So the line that constructs the SockJSBridge looks like:

    Vertx::SockJSServer.new(@server).bridge({'prefix' => '/eventbus'},
      [
        # Allow calls to get static album data from the persistor
        {
          'address' => 'demo.persistor',
          'match' => {
            'action' => 'find',
            'collection' => 'albums'
          }
        },
        # Allow user to login
        {
          'address' => 'demo.authMgr.login'
        }
      ])

Now restart the application

    vertx run app.rb
    
And refresh your browser.
>>>>>>> parent of bfa1e6a... Merge branch 'ws_beta10' into gh-pages

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

Copy the following into your editor and save it as `order_mgr.rb` in your tutorial top-level directory.

    require('vertx')
    require('json')

    @eb = Vertx::EventBus

    @eb.register_handler('demo.orderMgr') do |message|
      validateUser(message)
    end

    def validateUser(message)
      @eb.send('demo.authMgr.validate', { 'sessionID' => message.body['sessionID'] }) do |reply|
        if reply.body['status'] == 'ok'
          message.body['username'] = reply.body['username']
          saveOrder(message)
        else
          puts "Failed to validate user"
        end
      end
    end

    def saveOrder(message)
      @eb.send('demo.persistor',
          {'action' => 'save', 'collection' => 'orders', 'document' => message.body}) do |reply|
        if reply.body['status'] === 'ok'
          puts "order successfully processed!"
          message.reply({'status' => 'ok'}) # Reply to the front end
        else
          puts "Failed to save order"
        end
      end
    end

    
The order manager verticle registers a handler on the address `demo.orderMgr`. When any message arrives on the event bus a `Message` object is passed to the handler.

The actual order is in the `body` attribute of the message. When an order message arrives the first thing it does is sends a message to the authentication manager to validate if the user is logged in, given their session id (which is passed in the order message).

If the user was logged in ok, the order is then persisted using the MongoDB persistor. If that returns ok, we send back a message to the client.

All messages have a `reply` function which can be invoked to send back a reply to the sender of the message. In other words, it's an implementation of the *request-response* pattern.

We'll also need to add another accepted match on the SockJSBridge config in `web_server.rb` to tell it to let through orders:
    
    # Let through orders posted to the order manager
    {
      'address' => 'demo.orderMgr'
    }
    
So, it should look like:

    Vertx::SockJSServer.new(@server).bridge({'prefix' => '/eventbus'},
      [
        # Allow calls to get static album data from the persistor
        {
          'address' => 'demo.persistor',
          'match' => {
            'action' => 'find',
            'collection' => 'albums'
          }
        },
        # Allow user to login
        {
          'address' => 'demo.authMgr.login'
        },
        # Let through orders posted to the order manager
        {
          'address' => 'demo.orderMgr'
        }
      ])
    
We'll also have to add a line to `app.rb` to load the `order_mgr.rb` verticle, just before the web server is started:

    # Start the order manager

    Vertx.deploy_verticle('order_mgr.rb')

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
    
    vertx run app.js
    
Refresh the browser.

Now log-in and add a few items into your cart. Click on the cart tab and click "Submit Order". The message "Your order has been accepted, an email will be on your way to you shortly" should appear!

Take a look in the console window of the application. You should see the order has been logged.

![Client Application](tutorial_4.png)

** Congratulations! You have just placed an order. **
   
## Step 11. Securing the Connection

So far in this tutorial, all client-server traffic has been over an unsecured socket. That's not a very good idea since we've been sending login credentials and orders.

Configuring vert.x to use secure sockets is very easy. (For detailed information on configuring HTTPS, please
see the manual).

Edit `web_server.rb` again, and edit the line that creates the HTTP server so it reads:

    @server = HttpServer.new
    @server.ssl = true
    @server.key_store_path = 'server-keystore.jks'
    @server.key_store_password = 'wibble'
        
Copy the keystore from the distribution

    tim@Ethel:~/tutorial$ cp $VERTX_HOME/examples/ruby/webapp/server-keystore.jks . 
    
*The keystore is just a Java keystore which contains the certificate for the server. It can be manipulated using the Java `keytool` command.*           
        
You'll also need to edit `web/js/client_app.js` so the line which creates the client side event bus instance now uses `https` as the protocol:

    var eb = new vertx.EventBus('https://localhost:8080/eventbus');
    
Now restart the app again.

    vertx run app.js
    
And go to your browser. This time point your browser at `https://localhost:8080`. *Note it is **https** not http*.

*You'll initially get a warning from your browser saying the server certificate is unknown. This is to be expected since we haven't told the browser to trust it. You can ignore that for now. On a real server your server cert would probably be from a trusted certificate authority.*

Now login, and place an order as before.

Easy peasy. **It just works**

## Step 12. Scaling the application

### Scaling the web server

Scaling up the web server part is trivial. Simply start up more instances of the webserver. You can do this by changing the line that starts the verticle `web_server.js` to something like:

    # Start 32 instances of the web server!

    Vertx.deploy_verticle('web_server.rb', nil, 32)  
    
(*Vert.x is clever here, it notices that you are trying to start multiple servers on the same host and port, and internally it maintains a single listening server, but round robins connections between the various instances*.)

<<<<<<< HEAD
### More complex web applications

In this simple web application, there was no need to write any custom server side modules, but in more complex applications you might want to write your own server side services which can be used by clients (or by other server side code).

Doing this with Vert.x is very straightforward. Here's an example of a trivial server side service which listens on the event bus for messages and sends back the current time to the caller:

    require "vertx"
    include Vertx
    
    EventBus.register_handler("acme.timeService") do |message|
        message.reply({'current_time' => Time.now.to_i})        
    end

Save this in `time_service.rb`, and add a line in your `app.rb` to load it on startup.

Then you can just call it from client side JavaScript, or other server side components:

    eventBus.send("acme.timeService", null, function(reply) {
        console.log("Time is " + reply.current_time);
    });
    
### Packaging up your code as a Module

You can package up your entire application, or just individual Verticles as modules, so they can be easily reused by other applications, or started on the command line more easily.

For an explanation of how to do this, please see the modules manual.   
=======
### Scaling the processing.

In our trivial example it probably won't make much difference, but if you have some fairly intensive processing that needs to be done on the orders, it might make sense to maintain a farm of order processors, and as orders come into the order manager, to farm them out to one of the available processors.

You can then spread the processing load not just between multiple processors on the same machine, but between many processors on different machines of the network.

Doing this is easy with vert.x. Vert.x ships with an out-of-the-box busmod called `work-queue` which allows you to easily create queues of work can be shared out amongst many processors.

Please consult the busmods manual for more information on this.

### Packaging as a module

Vert.x applications and other functionality can be installed as modules. This makes them easier to manage and allow them to be easily referenced from other applications. For detailed information on modules, please see the modules manual.

Let's package our web application as a module.

By default modules live in the `mods` directory from the vert.x installation directory, but you can also set an environment variable `VERTX_MODS` to a directory of your choice where modules will be located. In this tutorial we'll just put the module in the `mods` directory for the sake of simplicity.

To install the app as a module we'll just copy the tutorial directory into the `mods` dir

    cd ..
    cp -r tutorial $VERTX_INSTALL/mods/webapp
   
Now create a file called `mod.json` which contains the following:

    {
        "main": "app.rb"
    }
    
And save it in the directory $VERTX_INSTALL/mods/webapp

That's it. The module is installed!

To run the module (first make sure the web app isn't already running, if so CTRL-C).
Then go to another console... you can be in any directory and type:

    vertx run webapp
    
The web application will now be running. Go to `https://localhost:8080` to see.        

## Final Thoughts

This tutorial gives you just a taste of the kinds of things you can do with vert.x. 

With a small amount of code you've created a real, scalable web-app.
>>>>>>> parent of bfa1e6a... Merge branch 'ws_beta10' into gh-pages

*Copies of this document may be made for your own use and for distribution to others, provided that you do not charge any fee for such copies and further provided that each copy contains this Copyright Notice, whether distributed in print or electronically.*


          

    

    


        





    
    




    


