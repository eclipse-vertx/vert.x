[TOC]

# Bus Modules

## What is a Bus Module (busmod) ?

A *busmod* is a verticle that communicates on the event bus with over verticles by sending JSON messages.

Since it communicates only with JSON messages then the busmod is instantly usable by other verticles irrespective of which language they are written in, since all the supported languages allow sending and receiving JSON messages from the event bus. 

JSON is the *lingua franca* of vert.x

You don't have to write your application modules as busmods but it's highly recommended you do since it will make them easily and consistently usable from other verticles.

There are a few more conventions that should be followed when writing busmods:

### Configuration

Configuration for a busmod (if any) should be specified using a JSON configuration file when deploying from the command line using `vertx run` or `vertx deploy` or passed in when deploying a busmod programmatically.

Applying configuration this way allows it to be easily and consistently configured irrespective of the language.

### Addresses

Each busmod will register one or more handlers at specific addresses so it can receive messages on the event bus. As a convention, the main address should be passed to the busmod in the JSON configuration.

If a busmod provides more than one function it often makes sense for it to register more than one handler. If it does so, a good convention for handler names is to use the main address as the root and create sub addresses from there, separated by dot `.`. For example, the `WorkQueue` busmod registers handlers at the following addresses, assuming the main address is `test.workQueue`

* `test.workQueue`. Main handler registered at this address. Messages sent to this address are added to the queue
* `test.workQueue.register . Messages sent to this address are requests to register a processor with the queue.
* `test.workQueue.unregister . Messages sent to this address are requests to unregister a processor with the queue.

### Clearing Up Handlers

Remember to unregister any handlers in the cleanup function of the verticle.
   
## Out of the box busmods

The vert.x distribution contains several out-of-the-box budmods that you can use in your applications. These busmods are designed to handle common things that you might want to do in applications.

#### Instantiating out-of-the-box busmods

You can instantiate any out of the box busmo from the command line using `vertx run` or `vertx deploy` like any other verticle, e.g.

    vertx run <bus_mode_name> -conf <config_file>
    
Or programmatically (e.g. in JavaScript)

    vertx.deployWorkerVerticle(<bus_mode_name>, <config>);        

### MongoDB Persistor

This busmod allows data to be saved, retrieved, searched for, and deleted in a MongoDB instance. MongoDB is a great match for persisting vert.x data since it natively handles JSON (BSON) documents. To use this busmod you must be have a MongoDB instance running on your network.

This is a worker busmod and must be started as a worker verticle.

#### Dependencies

This busmod requires a MongoDB server to be available on the network.

#### Name

The bus mod is written in Java, and the name is `org.vertx.java.busmods.persistor.MongoPersistor`.
            
There's also a JavaScript wrapper for it with the name `busmods/mongo_persistor.js`

#### Configuration

The MongoDB busmod requires the following configuration:

    {
        "address": <address>,
        "host": <host>,
        "port": <port>
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

* `address` The main address for the busmod. Every busmod has a main address.
* `host` Host name or ip address of the MongoDB instance. Defaults to `localhost`.
* `port` Port at which the MongoDB instance is listening. Defaults to `27017`.
* `db_name` Name of the database in the MongoDB instance to use. This parameter is mandatory. If you want to access different MongoDB databases you can create multiple instances of this busmod.

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
            "shoesize", 3.14159,
            "username", "tim",
            "password", "wibble"
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
        "match": <match>,
        "limit": <limit>,
        "batch_size": <batch_size>
    }     
    
Where:
* `collection` is the name of the MongoDB collection that you wish to search in in. This field is mandatory.
* `match` is a JSON object that you want to match against to find matching documents. This obeys the normal MongoDB matching rues.
* `limit` is a number which determines the maximum total number of documents to return. This is optional. By default all documents are returned.
* `batch_size` is a number which determines how many documents to return in each reply JSON message. It's optional and the default value is `100`. Batching is discussed in more detail below.

An example would be:

    {
        "action": "find",
        "collection": "orders",
        "match": {
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
        return new function(reply, replier) {
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
        "match": <match>
    }     
    
Where:
* `collection` is the name of the MongoDB collection that you wish to search in in. This field is mandatory.
* `match` is a JSON object that you want to match against to find a matching document. This obeys the normal MongoDB matching rues.

If more than one document matches, just the first one will be returned.

An example would be:

    {
        "action": "findone",
        "collection": "items",
        "match": {
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
        "match": <match>
    }     
    
Where:
* `collection` is the name of the MongoDB collection that you wish to delete from. This field is mandatory.
* `match` is a JSON object that you want to match against to delete matching documents. This obeys the normal MongoDB matching rues.

All documents that match will be deleted.

An example would be:

    {
        "action": "delete",
        "collection": "items",
        "match": {
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

The bus mod is written in Java, and the name is `org.vertx.java.busmods.mailer.Mailer`.
            
There's also a JavaScript wrapper for it with the name `busmods/mailer.js`.

Mailer is a worker busmod and must be started as a worker verticle.

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

This busmod verifies usernames and passwords in a MongoDB database and generates time-limited session ids. These session ids can be passed around the event bus.

The authentication manager can also verify a session id. This allows session ids to be passed around the event bus and validated if particular busmods want to find out if the user is logged in.

A typical usage would be:

* Login with username and password. Obtain session id.
* Do something (e.g. place an order). Pass session id with order message.
* Order busmod verifies session id before placing order. If verifies ok, then order is allowed to be placed.

Sessions time out after a certain amount of time. After that time, they will not verify as ok.

This busmod should not be run as a worker.

#### Dependencies

This busmod requires a MongoDB persistor busmod to be running to allow searching for usernames and passwords.

#### Name

The bus mod is written in Java, and the name is `org.vertx.java.busmods.auth.AuthManager`.
            
There's also a JavaScript wrapper for it with the name `busmods/auth_mgr.js`


#### Configuration

This busmod requires the following configuration:

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

* `address` The main address for the busmod. Every busmod has a main address.
* `user_collection` The MongoDB collection in which to search for usernames and passwords. This field is mandatory.
* `persistor_address` Address of the persistor busmod to use for usernames and passwords. This field is mandatory.
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

Once a processor has processed the work, it replies to the message and when the work queue receives the reply it removes the work from the queue. The processor can time out in processing a message, in which case the message becomes available again for other processors to consume.

Multiple processors can register for work with the work queue.

The work queue is useful for use cases where you have a lot of work to process and want to share this work out amongst many processors.

One example would be processing an order queue - each order must only be processed once, but we can have many processors (potentially on different machines) processing each order.

Another example would be in computational intensive tasks where the task can be split into N pieces and processed in parallel by different nodes. In other words, a compute cluster.

This busmod should not be run as a worker.

#### Dependencies

If this queue is persistent, the busmod requires a MongoDB persistor busmod to be running for persisting the queue data. [WIP]

#### Name

The bus mod is written in Java, and the name is `org.vertx.java.busmods.workqueue.WorkQueue`.
            
There's also a JavaScript wrapper for it with the name `busmods/work_queue.js`


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

Once the send has been accepted, and queued a reply message will be sent:

    {
        "status": "ok"
    }
    
If a problem occurs with the queueing, an error reply will be sent:

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

