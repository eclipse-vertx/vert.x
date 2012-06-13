[TOC]

# Modules

## What is a Module (mod) ?

Vert.x allows you to package up your applications or re-usable functionality into modules which can then be referenced by other applications or modules.

For example, vert.x ships with several out of the box modules including a web server, a mailer and a MongoDB persistor. You can also easily create your own modules.

### Modules location

Modules, by default, live in the `mods` directory in the vert.x installation directory. Vert.x comes with several modules pre-installed and they reside here.

Vert.x always first searches for modules in the `mods` directory of the installation followed by the directory given by the environment variable `VERTX_MODS`, if specified.

You can put your own modules in the `mods` directory if you like, but it is highly recommended you put them in some other directory (e.g. `~/vertx-mods`) so that you don't overwrite them when you upgrade vert.x.

If you do decide to put them somewhere else, make sure `VERTX_MODS` points to that directory.

Each module lives in its own child directory of the module directory. The name of the directory is the name of the module. Modules must not share a directory.

If you have more than one version of the same module, you should suffix the name and with a hash sign `#` followed by the version number. For example, here we have two versions of the widget module:

    mods/some-module
    mods/widget#v1.0
    mods/widget#v1.1

### Module directory structure

#### Module descriptor

Inside the module directory you must provide a file called `mod.json` which contains some JSON which describes the module, for example:

    {
        "main": "mailer.js"
    }
    
At minimum `mod.json` must contain a field `main` which specifies the main verticle to start the module. Main would be something like `myscript.groovy`, `app.rb`, `foo.js` or `org.acme.MyApp`. (See the chapter on "running vert.x" in the main manual for a description of what a main is.)

##### Worker modules

If your main verticle is a worker verticle you must also specify the `worker` field with value `true`, otherwise it will be assumed it is not a worker.

##### Preserving working directory

By default when you're module is executing it will see its working directory as the module directory when using the Vert.x API. This is useful if you want to package up an application which contains its own static resources into a module.

However in some cases, you don't want this behaviour, you want the module to see its working directory as the working directory of whoever started the module.

An example of this would be the `web-server` module. With the web server, you use it to serve your own static files which exist either outside of a module or in your own module, they do not exist inside the `web-server` module itself.

If you want to preserve current working directory, set the field `preserve-cwd` to `true` in `mod.json`. The default value is `false`.

#### Module path

Any scripts or classes you place in the module directory will be available to the module (placed on the module path). If you have any jar dependencies you can also place these in a directory called `lib` and these will be added to the module path.

Here's an example JS module:

    my-mod/mod.json
    my-mod/app.js
    my-mod/other-script.js
    my-mod/foo.json
    
In the above `app.js` is the main for the module. `other-script.js` and `foo.json` are scripts/resources used by the module.

    java-mod/mod.json
    java-mod/org/acme/MyMain.class
    java-mod/lib/other-jar.jar
    
The above is an example Java mod where `org/acme/MyMain.class` is the main, and `other-jar.jar` is a jar dependency of the module.

You can of course mix and match multiple languages in a single module.

### Running a module from the command line

Modules are run like any other verticle - i.e. using the `vertx run` or `vertx deploy` commands. Please see the main manual for a full description of this.

E.g.

    vertx run my-mod
    
Will run a module called `my-mod`    

### Running a module programmatically

You run a module programmatically in the same way as you run any verticle programmatically. Please see the core manual for the appropriate language for a full description on how to do this.

### Module working directory

When you run a module using `vertx run` or `vertx deploy` your actual process working directory is wherever you were when you executed the command.

However the module will be located in the `mods` (or `VERTX_MODS`) directory. Consequently if your module contains static files - e.g. your module might be a web application that serves static files from the file system, then if your module expects the working directory to be set to module directory it won't find them!

For example, let's say you have a simple webapp that has an HTTP server that serves an index.html which is packaged as a module:

    mods/my-web-app/server.js
    mods/my-web-app/web-root/index.html

And server.js serves the file with:

    req.response.sendFile('web-root/index.html');
    
In the above case the web server expects the working directory to be the module directory.

To solve this we internally adjust all paths such that, if you use the vert.x API for all file access then it will appear as if your working directory is the module directory. I.e. your web app will just work irrespective of where the module is actually installed :) 

If you don't want this behaviour you can set the field `preserve-cwd` to `true` in `mod.json`. See above for a description of this field.   

### Creating and installing your own module

Creating your own module is simple, simply create your vert.x application as normal, then put it all in a directory whose name is the module name, and provide a `mod.json` file as described above.

Then copy the entire directory to the module root directory (i.e. the `mods` directory in the install or your own module root given by `VERTX_MODS`)

### What is a Bus Module (busmod) ?

A *busmod* is a specific type of module that communicates on the event bus with over verticles by sending JSON messages.

Since it communicates only with JSON messages then the busmod is instantly usable by other verticles irrespective of which language they are written in, since all the supported languages allow sending and receiving JSON messages from the event bus. 

JSON is the *lingua franca* of vert.x

You don't have to write your application modules as busmods but it's highly recommended you do since it will make them easily and consistently usable from other verticles.

### Configuration

Configuration for a module (if any) should be specified using a JSON configuration file when deploying from the command line using `vertx run` or `vertx deploy` or passed in when deploying a busmod programmatically.

Applying configuration this way allows it to be easily and consistently configured irrespective of the language.

## Out of the box busmods

The vert.x distribution contains several out-of-the-box modules that you can use in your applications. These modules are designed to handle common things that you might want to do in applications.

#### Instantiating out-of-the-box modules

You can instantiate any out of the box module from the command line using `vertx run` or `vertx deploy` like any other verticle, i.e.

    vertx run <bus_mode_name> -conf <config_file>
    
For example:

    vertx run mongo-persistor -conf my_conf.json    
    
Or programmatically (e.g. in JavaScript)

    vertx.deployVerticle(<bus_mode_name>, <config>);        
    
For example:

    vertx.deployVerticle('mongo-persistor', {address: 'test.mypersistor', db_name: 'mydb'});    

### Web Server

This is a simple web server which efficiently serves files from the file system.

It can also be configured to act as an event bus bridge - bridging the server side event bus to client side JavaScript.

#### Name

The module name is `web-server`.

#### Configuration

The web-server configuration is as follows:

    {
        "web_root": <web_root>,
        "index_page": <index_page>,
        "host", <host>,
        "port", <port>
        "static_files": <static_files>,
        
        "ssl": <ssl>,        
        "key_store_password": <key_store_password>,
        "key_store_path": <key_store_path>,
        
        "bridge": <bridge>,
        "permitted": <permitted>,
        "sjs_config": <sjs_config>,
        "auth_timeout": <auth_timeout>,
        "auth_address": <auth_address>
    }
    
* `web-root`. This is the root directory from where files will be served. *Anything that you place here or in sub directories will be externally accessible*. Default is `web`.
* `index_page`. The page to serve when the root `/` is requested. Default is `index.html`.
* `host`. The host or ip address to listen at for connections. `0.0.0.0` means listen at all available addresses. Default is `0.0.0.0`.
* `port`. The port to listen at for connections. Default is `80`.
* `static_files`. Should the server serve static files? Default is `true`. 
* `ssl`. Should the server use `https` as a protocol? Default is `false`.
* `key_store_password`. Password of Java keystore which holds the server certificate. Only used if `ssl` is `true`. Default is `wibble`.
* `key_store_path`. Path to keystore which holds the server certificate. Default is `server-keystore.jks`. Only used if `ssl` is `true`. *Don't put the keystore under your webroot!*.
* `bridge`. Should the server also act as an event bus bridge. This is used when you want to bridge the event bus into client side JavaScript. Default is `false`.
* `permitted`. This is an array of JSON objects representing the permitted matches on the bridge. Only used if `bridge` is `true`. See the core manual for a full description of what these are. Defaults to `[]`.
* `sjs_config`. This is a JSON object representing the configuration of the SockJS bridging application. You'd normally use this for specifying the url at which SockJS will connect to bridge from client side JS to the server. Only used if `bridge` is `true`. Default to `{"prefix": "/eventbus"}`.
* `auth_timeout`. The bridge can also cache authorisations. This determines how long the bridge will cache one for. Default value is five minutes.
* `auth_address`. The bridge can also call an authorisation manager to do authorisation. This is the address to which it will send authorisation messages. Default value is `vertx.basicauthmanager.authorise`. 


##### Examples

Here are some example:

##### Simple static file web server

This serves files from the web directory 

    {
        "host": mycompany.com        
    {
    
##### Simple https server

    {
        "host": mycompany.com,
        "ssl": true,
        "key_store_path": "mycert.jks",
        "key_store_password": "sausages"
    }    
    
##### Event bus bridge    

Pure event bus bridge that doesn't serve static files

    {
       "host": "bridgeserver.mycompany.com",
       "static_files": false,
       "bridge": true,
       "permitted": [{"address":"myservice"}]       
    }
    
### MongoDB Persistor

This busmod allows data to be saved, retrieved, searched for, and deleted in a MongoDB instance. MongoDB is a great match for persisting vert.x data since it natively handles JSON (BSON) documents. To use this busmod you must be have a MongoDB instance running on your network.

This is a worker busmod and must be started as a worker verticle.

#### Dependencies

This busmod requires a MongoDB server to be available on the network.

#### Name

The module name is `mongo-persistor`.

#### Configuration

The mongo-persistor busmod takes the following configuration:

    {
        "address": <address>,
        "host": <host>,
        "port": <port>,
        "db_name": <db_name>    
    }
    
For example:

    {
        "address": "test.my_persistor",
        "host": "192.168.1.100",
        "port": 27000
        "db_name": "my_db"
    }        
    
Let's take a look at each field in turn:

* `address` The main address for the busmod. Every busmod has a main address. Defaults to `vertx.mongopersistor`.
* `host` Host name or ip address of the MongoDB instance. Defaults to `localhost`.
* `port` Port at which the MongoDB instance is listening. Defaults to `27017`.
* `db_name` Name of the database in the MongoDB instance to use. Defaults to `default_db`.

#### Operations

The busmod supports the following operations

##### Save

Saves a document in the database.

To save a document send a JSON message to the busmod main address:

    {
        "action": "save",
        "collection": <collection>,
        "document": <document>
    }     
    
Where:
* `collection` is the name of the MongoDB collection that you wish to save the document in. This field is mandatory.
* `document` is the JSON document that you wish to save.

An example would be:

    {
        "action": "save",
        "collection": "users",
        "document": {
            "name": "tim",
            "age": 1000,
            "shoesize": 3.14159,
            "username": "tim",
            "password": "wibble"
        }
    }  
    
When the save complete successfully, a reply message is sent back to the sender with the following data:

    {
        "status": "ok"
    }
    
The reply will also contain a field `_id` if the document that was saved didn't specifiy an id, this will be an automatically generated UUID, for example:

    {
        "status": "ok"
        "_id": "ffeef2a7-5658-4905-a37c-cfb19f70471d"
    }
     
If you save a document which already possesses an `_id` field, and a document with the same id already exists in the database, then the document will be updated. 
 
If an error occurs in saving the document a reply is returned:

    {
        "status": "error",
        "message": <message>
    }
    
Where `message` is an error message.    

            
##### Find    

Finds matching documents in the database.

To find documents send a JSON message to the busmod main address:

    {
        "action": "find",
        "collection": <collection>,
        "matcher": <matcher>,
        "limit": <limit>,
        "batch_size": <batch_size>
    }     
    
Where:
* `collection` is the name of the MongoDB collection that you wish to search in in. This field is mandatory.
* `matcher` is a JSON object that you want to match against to find matching documents. This obeys the normal MongoDB matching rues.
* `limit` is a number which determines the maximum total number of documents to return. This is optional. By default all documents are returned.
* `batch_size` is a number which determines how many documents to return in each reply JSON message. It's optional and the default value is `100`. Batching is discussed in more detail below.

An example would be:

    {
        "action": "find",
        "collection": "orders",
        "matcher": {
            "item": "cheese"
        }
    }  
    
This would return all orders where the `item` field has the value `cheese`. 

When the find complete successfully, a reply message is sent back to the sender with the following data:

    {
        "status": "ok",
        "results": <results>
    }   
    
Where `results` is a JSON array containing the results of the find operation. For example:

    {
        "status": "ok",
        "results": [
            {
                "user": "tim",
                "item": "cheese",
                "total": 123.45
            },
            {
                "user": "bob",
                "item": "cheese",
                "total": 12.23
            },
            {
                "user": "jane",
                "item": "cheese",
                "total": 50.05
            }
        ]
    }
    
If an error occurs in finding the documents a reply is returned:

    {
        "status": "error",
        "message": <message>
    }
    
Where `message` is an error message. 

###### Batching

If a find returns many documents we do not want to load them all up into memory at once and send them in a single JSON message since this could result in the server running out of RAM.

Instead, if there are more than `batch_size` documents to be found, the busmod will send a maxium of `batch_size` documents in each reply, and send multiple replies.

When you receive a reply to the find message containing `batch_size` documents the `status` field of the reply will be set to `more-exist` if there are more documents available.

To get the next batch of documents you just reply to the reply with an empty JSON message, and specify a reply handler in which to receive the next batch.

For instance, in JavaScript you might do something like:

    function processResults(results) {
        // Process the data
    }

    function createReplyHandler() {
        return function(reply, replier) {
            // Got some results - process them
            processResults(reply.results);
            if (reply.status === 'more-exist') {
                // Get next batch
                replier({}, createReplyHandler());
            }
        }
    }

    // Send the find request
    eb.send('foo.myPersistor', {
        action: 'find',
        collection: 'items',
        matcher: {}        
    }, createReplyHandler());
    
If there is more data to be requested and you do not reply to get the next batch within a timeout (10 seconds), then the underlying MongoDB cursor will be closed, and any further attempts to request more will fail.    
    

##### Find One  

Finds a single matching document in the database.

To find a document send a JSON message to the busmod main address:

    {
        "action": "findone",
        "collection": <collection>,
        "matcher": <matcher>
    }     
    
Where:
* `collection` is the name of the MongoDB collection that you wish to search in in. This field is mandatory.
* `matcher` is a JSON object that you want to match against to find a matching document. This obeys the normal MongoDB matching rues.

If more than one document matches, just the first one will be returned.

An example would be:

    {
        "action": "findone",
        "collection": "items",
        "matcher": {
            "_id": "ffeef2a7-5658-4905-a37c-cfb19f70471d"
        }
    }  
    
This would return the item with the specified id.

When the find complete successfully, a reply message is sent back to the sender with the following data:

    {
        "status": "ok",
        "result": <result>
    }       
    
If an error occurs in finding the documents a reply is returned:

    {
        "status": "error",
        "message": <message>
    }
    
Where `message` is an error message. 

##### Delete

Deletes a matching documents in the database.

To delete documents send a JSON message to the busmod main address:

    {
        "action": "delete",
        "collection": <collection>,
        "matcher": <matcher>
    }     
    
Where:
* `collection` is the name of the MongoDB collection that you wish to delete from. This field is mandatory.
* `matcher` is a JSON object that you want to match against to delete matching documents. This obeys the normal MongoDB matching rues.

All documents that match will be deleted.

An example would be:

    {
        "action": "delete",
        "collection": "items",
        "matcher": {
            "_id": "ffeef2a7-5658-4905-a37c-cfb19f70471d"
        }
    }  
    
This would delete the item with the specified id.

When the find complete successfully, a reply message is sent back to the sender with the following data:

    {
        "status": "ok",
        "number": <number>
    }       
    
Where `number` is the number of documents deleted.    
    
If an error occurs in finding the documents a reply is returned:

    {
        "status": "error",
        "message": <message>
    }
    
Where `message` is an error message. 


### Mailer

This busmod allows emails to be sent via SMTP.

#### Dependencies

This busmod requires a mail server to be available.

#### Name

The module name is `mailer`.

#### Configuration

The mailer busmod requires the following configuration:

    {
        "address": <address>,
        "host": <host>,
        "port": <port>,
        "ssl": <ssl>,
        "auth": <auth>,
        "username": <username>,
        "password": <password>   
    }
      
Let's take a look at each field in turn:

* `address` The main address for the busmod. Every busmod has a main address.
* `host` Host name or ip address of the maile server instance. Defaults to `localhost`.
* `port` Port at which the mail server is listening. Defaults to `25`.
* `ssl` If `true` then use ssl, otherwise don't use ssl. Default is `false`.
* `auth` If `true` use authentication, otherwise don't use authentication. Default is `false`.
* `username` If using authentication, the username. Default is `null`.
* `password` If using authentication, the password. Default is `null`.
 
For example, to send to a local server on port 25:

    {
        "address": "test.my_mailer"
    }  
    
Or to a gmail account:

    {
        "address": "test.my_mailer"
        "host": "smtp.googlemail.com",
        "port": 465,
        "ssl": true,
        "auth": true,
        "username": "tim",
        "password": "password"
    } 
    
#### Sending Emails

To send an email just send a JSON message to the main address of the mailer. The JSON message representing the email should have the following structure:

    {
        "from": <from>,
        "to": <to|to_list>,        
        "cc": <cc|cc_list>
        "bcc": <bcc|bcc_list>
        "subject": <subject>
        "body": <body>
    }

Where:

* `from` is the sender address of the email. Must be a well-formed email address.
* `to` to is either a single well formed email address representing the recipient or a JSON array of email addresses representing the recipients. This field is mandatory.
* `cc` to is either a single well formed email address representing a cc recipient or a JSON array of email addresses representing the cc list. This field is optional.
* `bcc` to is either a single well formed email address representing a bcc recipient or a JSON array of email addresses representing the bcc list. This field is optional.
* `subject` is the subject of the email. This field is mandatory.
* `body` is the body of the email. This field is mandatory.

For example, to send a mail to a single recipient:

    {
        "from": "tim@wibble.com",
        "to": "bob@wobble.com",                
        "subject": "Congratulations on your new armadillo!",
        "body": "Dear Bob, great to here you have purchased......"
    }

Or to send to multiple recipients:

    {
        "from": "tim@wibble.com",
        "to": ["bob@wobble.com", "jane@tribble.com"],                
        "subject": "Sadly, my aardvark George, has been arrested.",
        "body": "All, I'm afraid George was found...."
    }
    
    
### Authentication Manager

This is a basic authentication manager that verifies usernames and passwords in a MongoDB database and generates time-limited session ids. These session ids can be passed around the event bus.

The authentication manager can also authorise a session id. This allows session ids to be passed around the event bus and validated if particular busmods want to find out if the user is authorised.

Sessions time out after a certain amount of time. After that time, they will not verify as ok.

This busmod, is used in the web applicatio tutorial to handle simple user/password authorisation for the application.

#### Dependencies

This busmod requires a MongoDB persistor busmod to be running to allow searching for usernames and passwords.

#### Name

The module name is `auth-mgr`.

#### Configuration

This busmod takes the following configuration:

    {
        "address": <address>,
        "user_collection": <user_collection>,
        "persistor_address": <persistor_address>,
        "session_timeout": <session_timeout>   
    }
    
For example:

    {
        "address": "test.my_authmgr",
        "user_collection": "users",
        "persistor_address": "test.my_persistor",
        "session_timeout": 900000
    }        
    
Let's take a look at each field in turn:

* `address` The main address for the busmod. Optional field. Default value is `vertx.basicauthmanager`
* `user_collection` The MongoDB collection in which to search for usernames and passwords. Optional field. Default value is `users`.
* `persistor_address` Address of the persistor busmod to use for usernames and passwords. This field is optional. Default value is `vertx.mongopersistor`.
* `session_timeout` Timeout of a session, in milliseconds. This field is optional. Default value is `1800000` (30 minutes).

#### Operations

##### Login

Login with a username and password and obtain a session id if successful.

To login, send a JSON message to the address given by the main address of the busmod + `.login`. For example if the main address is `test.authManager`, you send the message to `test.authManager.login`.

The JSON message should have the following structure:

    {
        "username": <username>,
        "password": <password>
    }
    
If login is successful a reply will be returned:

    {
        "status": "ok",
        "sessionID": <sesson_id>    
    }
    
Where `session_id` is a unique session id.

If login is unsuccessful the following reply will be returned:

    {
        "status": "denied"    
    }
    
##### Logout

Logout and close a session. Any subsequent attempts to validate the session id will fail.

To login, send a JSON message to the address given by the main address of the busmod + `.logout`. For example if the main address is `test.authManager`, you send the message to `test.authManager.logout`.

The JSON message should have the following structure:

    {
        "sessionID": <session_id>
    }   
    
Where `session_id` is a unique session id. 
 
If logout is successful the following reply will be returned:

    {
        "status": "ok"    
    } 
    
Otherwise, if the session id is not known about:

    {
        "status": "error"    
    }   
    
##### Validate

Validates a session id.

To validate, send a JSON message to the address given by the main address of the busmod + `.validate`. For example if the main address is `test.authManager`, you send the message to `test.authManager.validate`.

The JSON message should have the following structure:

    {
        "sessionID": <session_id>
    }   
    
Where `session_id` is a unique session id. 
 
If the session is valid the following reply will be returned:

    {
        "status": "ok",
        "username": <username>    
    } 
    
Where `username` is the username of the user.    
    
Otherwise, if the session is not valid. I.e. it has expired or never existed in the first place.

    {
        "status": "denied"    
    }       
        
### Work Queue

This busmod queues messages (work) sent to it, and then forwards the work to one of many processors that may be attached to it, if available.

Once a processor has processed the work, it replies to the message and when the work queue receives the reply it removes the work from the queue. The reply is then forwarded back to the original sender. The processor can time out in processing a message, in which case the message becomes available again for other processors to consume.

The sender can also receive an optional reply when the work has been accepted by the work queue.

Multiple processors can register for work with the work queue.

The work queue is useful for use cases where you have a lot of work to process and want to share this work out amongst many processors.

One example would be processing an order queue - each order must only be processed once, but we can have many processors (potentially on different machines) processing each order.

Another example would be in computational intensive tasks where the task can be split into N pieces and processed in parallel by different nodes. In other words, a compute cluster.

This busmod should not be run as a worker.

#### Dependencies

If this queue is persistent, the busmod requires a MongoDB persistor busmod to be running for persisting the queue data. [WIP]

#### Name

The module name is `work-queue`.

#### Configuration

This busmod requires the following configuration:

    {
        "address": <address>,
        "process_timeout": <process_timeout>,
        "persistor_address": <persistor_address>,
        "collection": <collection>   
    }
    
Where:    

* `address` The main address for the busmod. Every busmod has a main address.
* `process_timeout` The processing timeout, in milliseconds for each item of work. If work is not processed before the timeout, it is returned to the queue and made available to other processors. This field is optional. Default value is `300000` (5 minutes).
* `persistor_address` If specified, this queue is persistent and this is the address of the persistor busmod to use for persistence. This field is optional.
* `collection` If persistent, the collection in the persistor where to persist the queue. This field is optional.

An example, non persistent configuration would be:

    {
        "address": "test.orderQueue"
    }
    
An example, persistent configuration would be:

    {
        "address": "test.orderQueue",
        "persistor_address": "test.myPersistor",
        "collection": "order_queue"
    }    

#### Operations

##### Send

To send data to the work queue, just send a JSON message to the main address of the busmod. The JSON message can have any structure you like - the work queue does not look at it.

Once the work has been sent out to a worker, and processed, and that worker has replied, the reply will be forwarded back to the sender.

You can optionally receive a reply when the work has been accepted (i.e. queued, but not yet processed), to do this add a field `accepted_reply` with a value holding the address where you want the reply sent. Once the send has been accepted, and queued a message will be sent to that address:

    {
        "status": "accepted"
    }
    
If a problem occurs with the queueing, an error reply will be sent to the `accepted_reply` address (if any).

    {
        "status": "error"
        "message": <message>
    }
    
Where `message` is an error message.    

##### Register

This is how a processor registers with the work queue. A processor is just an arbitrary handler on the event bus. To register itselfs as a processor, a JSON message is sent to the address given by the main address of the busmod + `.register`. For example if the main address is `test.orderQueue`, you send the message to `test.orderQueue.register`.

The message should have the following structure:

    {
        "processor": <processor>
    }

Where `processor` is the address of the processors handler. For example, if the processor has registered a handler at address `processor1`, then it would send the message:

    {
        "processor": "processor1"
    }    

When this message is received at the work queue, the work queue registers this address as a processor interested in work. When work arrives it will send the work off to any available processors, in a round-robin fashion.

When a processor receives some work, and has completed its processing. It should reply to the message with an empty reply. This tells the work queue that the work has been processed and can be forgotten about. If a reply is not received within `process_timeout` milliseconds then it will be assumed that the processor failed, and the work will be made available for other processors to process.

Once the register is complete, a reply message will be sent:

    {
        "status": "ok"
    }

##### Unregister

This is how a processor unregisters with the work queue. To unregister itselfs as a processor, a JSON message is sent to the address given by the main address of the busmod + `.unregister`. For example if the main address is `test.orderQueue`, you send the message to `test.orderQueue.unregister`.

The message should have the following structure:

    {
        "processor": <processor>
    }

Once the work queue receives the message, the processor will be unregistered and will receive no more work from the queue.

Once the unregister is complete, a reply message will be sent:

    {
        "status": "ok"
    }

*Copies of this document may be made for your own use and for distribution to others, provided that you do not charge any fee for such copies and further provided that each copy contains this Copyright Notice, whether distributed in print or electronically.*

