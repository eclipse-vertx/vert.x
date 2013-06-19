<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

[TOC]

# Writing Verticles

We previously discussed how a verticle is the unit of deployment in vert.x. Let's look in more detail about how to write a verticle.

As an example we'll write a simple TCP echo server. The server just accepts connections and any data received by it is echoed back on the connection.

Copy the following into a text editor and save it as `server.rb`

    require "vertx"
    include Vertx

    @server = NetServer.new.connect_handler { |socket|
      Pump.new(socket, socket).start
    }.listen(1234, 'localhost')

    def vertx_stop
      @server.close
    end

Now, go to the directory where you saved the file and type

    vertx run server.rb

The server will now be running. Connect to it using telnet:

    telnet localhost 1234

And notice how data you send (and hit enter) is echoed back to you.

Congratulations! You've written your first verticle.

## Accessing the Vert.x API

If you want to access the vert.x core API from within your verticle (which you almost certainly want to do), you need to require it with `require 'vertx'` at the top of your script. Normally this will be the first thing at the top of your verticle main.

This will import the module `Vertx`. The `Vertx` module contains the various classes and methods that make up the vert.x core API.

## Verticle clean-up

Servers, clients and event bus handlers will be automatically closed when the verticles is stopped, however if you need to provide any custom clean-up code when the verticle is stopped you can provide a `vertx_stop` top-level method. Vert.x will then call this when the verticle is stopped. 

## Getting Configuration in a Verticle

If JSON configuration has been passed when deploying a verticle from either the command line using `vertx run` or `vertx deploy` and specifying a configuration file, or when deploying programmatically, that configuration is available to the verticle using the `Vertx.config` method. For example:

    config = Vertx.config;

    # Do something with config
    
    puts "number of wibbles is #{config.wibble_number}"

The config returned is a Ruby Hash. You can use this object to configure the verticle. Allowing verticles to be configured in a consistent way like this allows configuration to be easily passed to them irrespective of the language.

## Logging from a Verticle

Each verticle is given its own logger. To get a reference to it invoke the `Vertx.logger` method:

    logger = Vertx.logger

    logger.info 'I am logging something'

The logger has the functions:

* trace
* debug
* info
* warn
* error
* fatal

Which have the normal meanings you would expect.

The log files by default go in a file called `vertx.log` in the system temp directory. On my Linux box this is `\tmp`.

For more information on configuring logging, please see the main manual.

## Accessing environment variables from a Verticle

You can access environment variables from a Verticle with the method `Vertx.env`.

# Deploying and Undeploying Verticles Programmatically

You can deploy and undeploy verticles programmatically from inside another verticle. Any verticles deployed programmatically inherit the path of the parent verticle.

## Deploying a simple verticle

To deploy a verticle programmatically call the function `Vertx.deploy_verticle`. The return value of `Vertx.deploy_verticle` is the unique id of the deployment, which can be used later to undeploy the verticle.

To deploy a single instance of a verticle :

    Vertx.deploy_verticle('my_verticle.rb')
    
## Deploying a module programmatically

You should use `deploy_module` to deploy a module, for example:

    container.deploy_module('vertx.mailer-v1.0', config)

Would deploy an instance of the `vertx.mailer` module with the specified configuration. Please see the modules manual
 for more information about modules.

## Passing configuration to a verticle programmatically

JSON configuration can be passed to a verticle that is deployed programmatically. Inside the deployed verticle the configuration is accessed with the `vertx.config` function. For example:

    var config = { name: 'foo', age: 234 }
    Vertx.deploy_verticle('my_verticle.rb', config)

Then, in `my_verticle.rb` you can access the config via `vertx.config` as previously explained.

## Using a Verticle to co-ordinate loading of an application

If you have an application that is composed of multiple verticles that all need to be started at application start-up, then you can use another verticle that maintains the application configuration and starts all the other verticles. You can think of this as your application starter verticle.

For example, you could create a verticle `app.rb` as follows:

    # Application config

    appConfig = {
        :verticle1_config => {
            # Config for verticle1
        },
        :verticle2_config => {
            # Config for verticle2
        },
        :verticle3_config => {
            # Config for verticle3
        },
        :verticle4_config => {
            # Config for verticle4
        },
        :verticle5_config => {
            # Config for verticle5
        }
    }

    # Start the verticles that make up the app

    Vertx.deploy_verticle("verticle1.rb", appConfig[:verticle1_config])
    Vertx.deploy_verticle("verticle2.rb", appConfig[:verticle2_config], 5)
    Vertx.deploy_verticle("verticle3.rb", appConfig[:verticle3_config])
    Vertx.deploy_worker_verticle("verticle4.rb", appConfig[:verticle4_config])
    Vertx.deploy_worker_verticle("verticle5.rb", appConfig[:verticle5_config], 10)


Then you can start your entire application by simply running:

    vertx run app.rb

or

    vertx deploy app.rb

## Specifying number of instances

By default, when you deploy a verticle only one instance of the verticle is deployed. If you want more than one instance to be deployed, e.g. so you can scale over your cores better, you can specify the number of instances in the third parameter to `deploy_verticle`:

    Vertx.deploy_verticle('my_verticle.rb', nil, 10)

The above example would deploy 10 instances.

## Getting Notified when Deployment is complete

The actual verticle deployment is asynchronous and might not complete until some time after the call to `deploy_verticle` has returned. If you want to be notified when the verticle has completed being deployed, you can pass a block to `deploy_verticle`, which will be called when it's complete:

    Vertx.deploy_verticle('my_verticle.rb', nil, 10) { puts "It's been deployed!" }

## Deploying Worker Verticles

The `Vertx.deploy_verticle` method deploys standard (non worker) verticles. If you want to deploy worker verticles use the `Vertx.deploy_worker_verticle` function. This function takes the same parameters as `Vertx.deploy_verticle` with the same meanings.

## Undeploying a Verticle

Any verticles that you deploy programmatically from within a verticle and all of their children are automatically undeployed when the parent verticle is undeployed, so in most cases you will not need to undeploy a verticle manually, however if you do want to do this, it can be done by calling the function `Vertx.undeploy_verticle` passing in the deployment id that was returned from the call to `Vertx.deploy_verticle`

    deploymentID = Vertx.deploy_verticle('my_verticle.rb') {
        # Immediately undeploy it
        vertx.undeployVerticle(deploymentID);
    }   

# The Event Bus

The event bus is the nervous system of vert.x.

It allows verticles to communicate with each other irrespective of what language they are written in, and whether they're in the same vert.x instance, or in a different vert.x instance. It even allows client side JavaScript running in a browser to communicate on the same event bus. (More on that later).

It creates a distributed polyglot overlay network spanning multiple server nodes and multiple browsers.

The event bus API is incredibly simple. It basically involves registering handlers, unregistering handlers and sending messages.

First some theory:

## The Theory

### Addressing

Messages are sent on the event bus to an *address*.

Vert.x doesn't bother with any fancy addressing schemes. In vert.x an address is simply a string, any string is valid. However it is wise to use some kind of scheme, e.g. using periods to demarcate a namespace.

Some examples of valid addresses are `europe.news.feed1`, `acme.games.pacman`, `sausages`, and `X`.

### Handlers

A handler is a thing that receives messages from the bus. You register a handler at an address.

Many different handlers from the same or different verticles can be registered at the same address. A single handler can be registered by the verticle at many different addresses.

### Publish / subscribe messaging

The event bus supports *publishing* messages. Messages are published to an address. Publishing means delivering the message to all handlers that are registered at that address. This is the familiar *publish/subscribe* messaging pattern.

### Point to point messaging

The event bus supports *point to point* messaging. Messages are sent to an address. This means a message is delivered to *at most* one of the handlers registered at that address. If there is more than one handler regsitered at the address, one will be chosen using a non-strict round-robin algorithm.

With point to point messaging, an optional reply handler can be specified when sending the message. When a message is received by a recipient, and has been *processed*, the recipient can optionally decide to reply to the message. If they do so that reply handler will be called.

When the reply is received back at the sender, it too can be replied to. This can be repeated ad-infinitum, and allows a dialog to be set-up between two different verticles. This is a common messaging pattern called the *Request-Response* pattern.

### Transient

*All messages in the event bus are transient, and in case of failure of all or parts of the event bus, there is a possibility messages will be lost. If your application cares about lost messages, you should code your handlers to be idempotent, and your senders to retry after recovery.*

If you want to persist your messages you can use a persistent work queue module for that.

### Types of messages

Messages that you send on the event bus can be as simple as a string, a number or a boolean. You can also send vert.x buffers or JSON messages.

It's highly recommended you use JSON messages to communicate between verticles. JSON is easy to create and parse in all the languages that vert.x supports.

## Event Bus API

Let's jump into the API

### Registering and Unregistering Handlers

To set a message handler on the address `test.address`, you call the method `registerHandler` on the `EventBus` class

    id = Vertx::EventBus.register_handler('test.address') do |message|
        puts "Got message body #{message.body}" 
    end

It's as simple as that. The handler will then receive any messages sent to that address. The object passed into the handler is an instance of class `Message`. The body of the message is available via the `body` attribute. 

The return value of `register_handler` is a unique handler id which can used later to unregister the handler.

When you register a handler on an address and you're in a cluster it can take some time for the knowledge of that new handler to be propagated across the entire cluster. If you want to be notified when that has completed you can optionally specify a block to the `register_handler` method as the third argument. This block will then be called once the information has reached all nodes of the cluster. E.g. :

    Vertx::EventBus.register_handler('test.address', myHandler) do
        puts 'Yippee! The handler info has been propagated across the cluster'
    end

To unregister a handler it's just as straightforward. You simply call `unregister_handler` passing in the id of the handler:

    Vertx::EventBus.unregister_handler('test.address', id);

As with registering, when you unregister a handler and you're in a cluster it can also take some time for the knowledge of that unregistration to be propagated across the entire to cluster. If you want to be notified when that has completed you can optionally specify another block to the `register_handler` method:

    Vertx::EventBus.unregister_handler(id) do
        puts 'Yippee! The handler unregister has been propagated across the cluster'
    end

If you want your handler to live for the full lifetime of your verticle there is no need to unregister it explicitly - vert.x will automatically unregister any handlers when the verticle is stopped.  

### Publishing messages

Publishing a message is also trivially easy. Just publish it specifying the address, for example:

    Vertx::EventBus.send('test.address", 'hello world')

That message will then be delivered to any handlers registered against the address "test.address".

### Sending messages

Sending a message will result in at most one handler registered at the address receiving the message. This is the point to point messaging pattern.

    Vertx::EventBus.send('test.address", 'hello world')

### Replying to messages

Sometimes after you send a message you want to receive a reply from the recipient. This is known as the *request-response pattern*.

To do this you send a message, and specify a block as a reply handler. When the receiver receives the message there is a method `reply` on the `Message` instance that is passed into the handler.

When this function is invoked it causes a reply to be sent back to the sender where the reply handler is invoked. An example will make this clear:

The receiver:

    Vertx::EventBus.registerHandler('test.address') do |message|
      puts("I received a message #{message.body}")

      # Do some stuff...
      # Now reply to it

      message.reply('This is a reply')
    end

The sender:

    Vertx::EventBus.send('test.address', 'This is a message') do |message|
      puts("I received a reply #{message.body}")    
    end

It is legal also to send an empty reply or null reply.

The replies themselves can also be replied to so you can create a dialog between two different verticles consisting of multiple rounds.

### Message types

The message you send can be any of the following types:

* number
* String
* boolean
* JSON object
* Vert.x Buffer

Vert.x buffers and JSON objects are copied before delivery if they are delivered in the same JVM, so different verticles can't access the exact same object instance.

Here are some more examples:

Send some numbers:

    Vertx::EventBus.send('test.address', 1234)
    Vertx::EventBus.send('test.address', 3.14159)

Send a boolean:

    Vertx::EventBus.send('test.address', true)

Send a JSON object:

    myObj = {
      'name' => 'Tim',
      'address' => 'The Moon',
      'age' => 457
    }
    Vertx::EventBus.send('test.address', myObj)

Nil messages can also be sent:

    Vertx::EventBus.send('test.address', nil)

It's a good convention to have your verticles communicating using JSON.

## Distributed event bus

To make each vert.x instance on your network participate on the same event bus, start each vert.x instance with the `-cluster` command line switch.

See the chapter in the main manual on running vert.x for more information on this.

Once you've done that, any vert.x instances started in cluster mode will merge to form a distributed event bus.

# Shared Data

Sometimes it makes sense to allow different verticles instances to share data in a safe way. Vert.x allows simple *Hash* and *Set* data structures to be shared between verticles.

There is a caveat: To prevent issues due to mutable data, vert.x only allows simple immutable types such as number, boolean and string or Buffer to be used in shared data. With a Buffer, it is automatically copied when retrieved from the shared data, so different verticle instances never see the same object instance.

Currently data can only be shared between verticles in the *same vert.x instance*. In later versions of vert.x we aim to extend this to allow data to be shared by all vert.x instances in the cluster.

## Shared Hashes

To use a shared hash to share data between verticles first get a reference to the hash, and then we just use standard Hash operations to put and get the data.

    hash = Vertx::SharedData.getHash('demo.myhash')

    hash['some-key'] = 'some-value'

And then, in a different verticle:

    hash = Vertx::SharedData.getHash('demo.myhash')

    puts("value of some-key is #{hash['some-key']}")

**TODO** More on map API

## Shared Sets

To use a shared set to share data between verticles first get a reference to the set.

    set = Vertx::SharedData.getSet('demo.myset')

    set.add('some-value');

And then, in a different verticle:

    set = Vertx::SharedData.getSet('demo.myset')

    # Do something with the set

**TODO** - More on set API

API - atomic updates etc

# Buffers

Most data in vert.x is shuffled around using instances of `Vertx::Buffer`.

A Buffer represents a sequence of zero or more bytes that can be written to or read from, and which expands automatically as necessary to accomodate any bytes written to it.

## Creating Buffers

Create an empty buffer

    buff = Vertx::Buffer.create

Create a buffer from a String. The String will be encoded in the buffer using UTF-8.

    buff = Vertx::Buffer.create_from_str("some-string")
    
Create a buffer from a String: The String will be encoded using the specified encoding, e.g:

    buff = Vertx::Buffer.create_from_str("some-string", "UTF-16")
    
Create a buffer with an initial size hint. If you know your buffer will have a certain amount of data written to it you can create the buffer and specify this size. This makes the buffer initially allocate that much memory and is more efficient than the buffer automatically resizing multiple times as data is written to it.

Note that buffers created this way *are empty*. It does not create a buffer filled with zeros up to the specified size.
        
    buff = Vertx::Buffer.create(100000)        
    
## Writing to a Buffer

There are two ways to write to a buffer: appending, and random access. In either case buffers will always expand automatically to encompass the bytes. It's not possible to write outside the bounds of the buffer.

### Appending to a Buffer

To append to a buffer, you use the `append_XXX` methods. Append methods exist for appending other buffers, String, Float and FixNum.

When appending a FixNum you have to specify how many bytes you want to append this as in the buffer. Valid values are 1, 2, 4, 8. All FixNums are appended as signed integer values.

When appending a Float you have to specify how many bytes you want to append this as in the buffer. Valid values are 4, 8, representing a single precision 32-bit IEEE 754 and a double precision 64-bit IEEE 754 floating point number respectively.

The return value of the `append_XXX` methods is the buffer itself, so these can be chained:

    buff = Vertx::Buffer.create
    
    buff.append_fixnum(100, 1) # Append a single byte in the buffer
    buff.append_fixnum(231243, 8) # Append number as 8 bytes in the buffer
    buff.append_str('foo').append_float(23.4232, 4) # Appends can be chained
    
    socket.write_buffer(buff)
    
Appending FixNums:

    buff.append_fixnum(100, 1)   # Append number as single signed byte
    
    buff.append_fixnum(100, 2)   # Append number as signed integer in two bytes
    
    buff.append_fixnum(100, 4)   # Append number as signed integer in four bytes    
    
    buff.append_fixnum(100, 8)   # Append number as signed integer in eight bytes    
    
Appending Floats:

    buff.append_float(12.234, 4)    # Append number as a 32-bit IEEE 754 floating point number (4 bytes)
    
    buff.append_float(12.234, 8)    # Append number as a 64-bit IEEE 754 floating point number (8 bytes)    
    
Appending buffers

    buff.append_buffer(other_buffer)    # Append other_buffer to buff    
    
Appending strings

    buff.append_str(str)                      # Append string as UTF-8 encoded bytes    
    
    buff.append_str(str, 'UTF-16')            # Append string as sequence of bytes in specified encodingt

### Random access buffer writes

You can also write into the buffer at a specific index, by using the `set_XXX` methods. Set methods exist for other buffers, String, Float and FixNum. All the set methods take an index as the first argument - this represents the position in the buffer where to start writing the data.

When setting a FixNum you have to specify how many bytes you want to set this as in the buffer. Valid values are 1, 2, 4, 8.

When setting a Float you have to specify how many bytes you want to set this as in the buffer. Valid values are 4, 8.

The buffer will always expand as necessary to accomodate the data.

    buff = Vertx::Buffer.create
    
    buff.set_fixnum(0, 4, 123123) # Set number as 4 bytes written at index 0 
    buff.set_float(1000, 8, 414.123123123) # Set float as 8 bytes at index 1000
    
To set FixNums:

    buff.set_fixnum(100, 123, 1)    # Set number as a single signed byte at position 100     
    
    buff.set_fixnum(100, 123, 2)    # Set number as a signed two byte integer at position 100         
    
    buff.set_fixnum(100, 123, 4)    # Set number as a signed four byte integer at position 100             
    
    buff.set_fixnum(100, 123, 8)    # Set number as a signed eight byte integer at position 100             
    
To set Floats:

    buff.set_float(100, 1.234, 4)   # Set the number as a 32-bit IEEE 754 floating point number (4 bytes) at pos 100   
    
    buff.set_float(100, 1.234, 8)   # Set the number as a 64-bit IEEE 754 floating point number (4 bytes) at pos 100    
    
To set a buffer

    buff.set_buffer(100, other_buffer)
    
To set a string

    buff.set_string(100, str) # Set the string using UTF-8 encoding        
           
    buff.set_string(100, str, 'UTF-16') # Set the string using the specified encoding
           
## Reading from a Buffer

Data is read from a buffer using the `get_XXX` methods. Get methods exist for byte, FixNum and Float. The first argument to these methods is an index in the buffer from where to get the data.

When reading FixNum values the data in the buffer is interpreted as a signed integer value.

    num = buff.get_byte(100)                 # Get a byte from pos 100 in buffer
    
    num = buff.get_fixnum(100, 1)            # Same as get_byte
    
    num = buff.get_fixnum(100, 2)            # Get two bytes as signed integer from pos 100
    
    num = buff.get_fixnum(100, 4)            # Get four bytes as signed integer from pos 100 
    
    num = buff.get_fixnum(100, 8)            # Get eight bytes as signed integer from pos 100       
    
Floats:

    num = buff.get_float(100, 4)             # Get four bytes as a 32-bit IEEE 754 floating point number from pos 100
    
    num = buff.get_float(100, 8)             # Get eight bytes as a 32-bit IEEE 754 floating point number from pos 100    

Strings:

    str = buff.get_string(100, 110)           # Get 10 bytes from pos 100 interpreted as UTF-8 string
    
    str = buff.get_string(100, 110, 'UTF-16') # Get 10 bytes from pos 100 interpreted in specified encoding
    
Buffers:

    other_buff = buff.get_buffer(100, 110)    # Get 10 bytes as a new buffer starting at position 100      
    
    
## Other buffer methods:

* `length`: To obtain the length of the buffer. The length of a buffer is the index of the byte in the buffer with the largest index + 1.
* `copy`: Copy the entire buffer

See the Yardoc for more detailed method level documentation.    

# Delayed and Periodic Tasks

It's very common in vert.x to want to perform an action after a delay, or periodically.

In standard verticles you can't just make the thread sleep to introduce a delay, as that will block the event loop thread.

Instead you use vert.x timers. Timers can be *one-shot* or *periodic*. We'll discuss both

## One-shot Timers

A one shot timer calls an event handler after a certain delay, expressed in milliseconds.

To set a timer to fire once you use the `Vertx.set_timer` function passing in the delay and specifying a handler block which will be called when after the delay:

    Vertx.set_timer(1000) do
        puts 'And one second later this is printed'
    end

    puts 'First this is printed'

## Periodic Timers

You can also set a timer to fire periodically by using the `set_periodic` function. There will be an initial delay equal to the period. The return value of `set_periodic` is a unique timer id (number). This can be later used if the timer needs to be cancelled. The argument passed into the timer event handler is also the unique timer id:

    id = Vertx.set_timer(1000) do
        puts 'And every second this is printed'
    end

    puts 'First this is printed'

## Cancelling timers

To cancel a periodic timer, call the `cancel_timer` function specifying the timer id. For example:

    id = Vertx.set_periodic(1000) do
        # This will never be called
    end

    # And immediately cancel it

    Vertx.cancel_timer(id)

Or you can cancel it from inside the event handler. The following example cancels the timer after it has fired 10 times.

    count = 0

    Vertx.set_periodic(1000) do
        puts "In event handler #{count}"
        count += 1
        vertx.cancelTimer(id) if count == 10        
    end


# Writing TCP Servers and Clients

Creating TCP servers and clients is incredibly easy with vert.x.

## Net Server

### Creating a Net Server

To create a TCP server we simply create an instance of Vertx::NetServer

    server = Vertx::NetServer.new

### Start the Server Listening

To tell that server to listen for connections we do:

    server = Vertx::NetServer.new

    server.listen(1234, 'myhost')

The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If it is ommitted it will default to `0.0.0.0` which means it will listen at all available interfaces.


### Getting Notified of Incoming Connections

Just having a TCP server listening creates a working server that you can connect to (try it with telnet!), however it's not very useful since it doesn't do anything with the connections.

To be notified when a connection occurs we need to call the `connect_handler` function of the server, specifying a block which represents the handler. The handler will be called when a connection is made:

    server = Vertx::NetServer.new

    server.connect_handler { puts 'A client has connected!' }

    server.listen(1234, 'localhost')

That's a bit more interesting. Now it displays 'A client has connected!' every time a client connects.

The return value of the `connect_handler` method is the server itself, so multiple invocations can be chained together. That means we can rewrite the above as:

    server = Vertx::NetServer.new

    server.connect_handler do
        puts 'A client has connected!'
    end.listen(1234, 'localhost')

or

    Vertx::NetServer.new.connect_handler do
        puts 'A client has connected!'
    end.listen(1234, 'localhost')


This is a common pattern throughout the vert.x API.


### Closing a Net Server

To close a net server just call the `close` method.

    server.close

The close is actually asynchronous and might not complete until some time after the `close` method has returned. If you want to be notified when the actual close has completed then you can specify a block to the `close` function.

This block will then be called when the close has fully completed.

    server.close { puts 'The server is now fully closed.' }
    
In most cases you don't need to close a net server explicitly since vert.x will close them for you when the verticle stops.    


### NetServer Properties

NetServer has a set of attributes you can set which affect its behaviour. Firstly there are bunch of attributes used to tweak the TCP parameters, in most cases you won't need to set these:

* `tcp_no_delay` If this attribute is true then [Nagle's Algorithm](http://en.wikipedia.org/wiki/Nagle's_algorithm) is disabled. If false then it is enabled.

* `send_buffer_size` Sets the TCP send buffer size in bytes.

* `receive_buffer_size` Sets the TCP receive buffer size in bytes.

* `tcp_keep_alive` if this attribute is true then [TCP keep alive](http://en.wikipedia.org/wiki/Keepalive#TCP_keepalive) is enabled, if false it is disabled.

* `reuse_address` if this attribute is true then addresses in TIME_WAIT state can be reused after they have been closed.

* `so_linger`

* `traffic_class`

NetServer has a further set of properties which are used to configure SSL. We'll discuss those later on.


### Handling Data

So far we have seen how to create a `NetServer`, and accept incoming connections, but not how to do anything interesting with the connections. Let's remedy that now.

When a connection is made, the connect handler is called passing in an instance of `NetSocket`. This is a socket-like interface to the actual connection, and allows you to read and write data as well as do various other things like close the socket.


#### Reading Data from the Socket

To read data from the socket you need to set the `data_handler` on the socket. This handler will be called with a `Buffer` every time data is received on the socket. You could try the following code and telnet to it to send some data:

    server = Vertx::NetServer.new

    server.connect_handler do |sock|

        sock.data_handler do |buffer|
            puts "I received #{buffer.length} bytes of data"
        end

    end.listen(1234, 'localhost')

#### Writing Data to a Socket

To write data to a socket, you invoke one of the write methods. 

With a single buffer:

    myBuffer = Vertx::Buffer.create(...)
    sock.write_buffer(myBuffer)

A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    sock.write_str('hello')

A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.

    sock.write_str('hello', 'UTF-16')

The write methods are asynchronous and always returns immediately after the write has been queued.

The actual write might occur some time later. If you want to be informed when the actual write has happened you can specify a block to the method:

This block will then be invoked when the write has completed:

    sock.write_str('hello') { puts 'It has actually been written' }

Let's put it all together.

Here's an example of a simple TCP echo server which simply writes back (echoes) everything that it receives on the socket:

    server = Vertx::NetServer.new

    server.connect_handler do |sock|

        sock.data_handler do |buffer|
            sock.write(buffer)
        end

    end.listen(1234, 'localhost');

### Closing a socket

You can close a socket by invoking the `close` method. This will close the underlying TCP connection.

### Closed Handler

If you want to be notified when a socket is closed, you can set the `closed_handler':

    server = Vertx::NetServer.new

    server.connect_handler do |sock|

        sock.closed_handler { puts 'The socket is now closed' }
        
    end

The closed handler will be called irrespective of whether the close was initiated by the client or server.

### Exception handler

You can set an exception handler on the socket that will be called if an exception occurs:

    server = Vertx::NetServer.new

    server.connect_handler do |sock|

        sock.exception_handler { puts 'Oops. Something went wrong' }
    end


### Read and Write Streams

NetSocket also can at as a `ReadStream` and a `WriteStream`. This allows flow control to occur on the connection and the connection data to be pumped to and from other object such as HTTP requests and responses, WebSockets and asynchronous files.

This will be discussed in depth in the chapter on streams and pumps.

## Scaling TCP Servers

A verticle instance is strictly single threaded.

If you create a simple TCP server and deploy a single instance of it then all the handlers for that server are always executed on the same event loop (thread).

This means that if you are running on a server with a lot of cores, and you only have this one instance deployed then you will have at most one core utilised on your server! That's not very good, right?

To remedy this you can simply deploy more instances of the verticle in the server, e.g.

    vertx run echo_server.rb -instances 20

The above would run 20 instances of echo_server.rb to a locally running vert.x instance.

Once you do this you will find the echo server works functionally identically to before, but, *as if by magic*, all your cores on your server can be utilised and more work can be handled.

At this point you might be asking yourself *'Hold on, how can you have more than one server listening on the same host and port? Surely you will get port conflicts as soon as you try and deploy more than one instance?'*

*Vert.x does a little magic here*.

When you deploy another server on the same host and port as an existing server it doesn't actually try and create a new server listening on the same host/port.

Instead it internally maintains just a single server, and, as incoming connections arrive it distributes them in a round-robin fashion to any of the connect handlers set by the verticles.

Consequently vert.x TCP servers can scale over available cores while each vert.x verticle instance remains strictly single threaded, and you don't have to do any special tricks like writing load-balancers in order to scale your server on your multi-core machine.

## NetClient

A NetClient is used to make TCP connections to servers.

### Creating a Net Client

To create a TCP client we simply create an instance of `Vertx::NetClient`.

    client = Vertx::NetClient.new

### Making a Connection

To actually connect to a server you invoke the `connect` method

    client = Vertx::NetClient.new

    client.connect(1234, 'localhost') do |sock|
        puts 'We have connected'
    end

The connect method takes the port number as the first parameter, followed by the hostname or ip address of the server. It takes a block as the connect handler. This handler will be called when the connection actually occurs.

The argument passed into the connect handler is an instance of `NetSocket`, exactly the same as what is passed into the server side connect handler. Once given the `NetSocket` you can read and write data from the socket in exactly the same way as you do on the server side.

You can also close it, set the closed handler, set the exception handler and use it as a `ReadStream` or `WriteStream` exactly the same as the server side `NetSocket`.

### Catching exceptions on the Net Client

You can set an exception handler on the `NetClient`. This will catch any exceptions that occur during connection.

    client = Vertx::NetClient.new

    client.exception_handler do |ex|
      puts 'Cannot connect since the host does not exist!'
    end

    client.connect(4242, 'host-that-doesnt-exist') do |sock|
      puts 'this won't get called'
    end


### Configuring Reconnection

A NetClient can be configured to automatically retry connecting or reconnecting to the server in the event that it cannot connect or has lost its connection. This is done by invoking setting the attributes `reconnect_attempts` and `reconnect_interval`:

    client = Vertx::NetClient.new

    client.reconnect_attempts = 1000

    client.reconnect_interval = 500

`reconnect_attempts` determines how many times the client will try to connect to the server before giving up. A value of `-1` represents an infinite number of times. The default value is `0`. I.e. no reconnection is attempted.

`reconnect_interval` determines how long, in milliseconds, the client will wait between reconnect attempts. The default value is `1000`.

If an exception handler is set on the client, and reconnect attempts is not equal to `0`. Then the exception handler will not be called until the client gives up reconnecting.


### NetClient Properties

Just like `NetServer`, `NetClient` also has a set of TCP properties you can set which affect its behaviour. They have the same meaning as those on `NetServer`.

`NetClient` also has a further set of properties which are used to configure SSL. We'll discuss those later on.

## SSL Servers

Net servers can also be configured to work with [Transport Layer Security](http://en.wikipedia.org/wiki/Transport_Layer_Security) (previously known as SSL).

When a `NetServer` is working as an SSL Server the API of the `NetServer` and `NetSocket` is identical compared to when it working with standard sockets. Getting the server to use SSL is just a matter of configuring the `NetServer` before `listen` is called.

To enabled SSL set the attribute `ssl` to `true` on the `NetServer`.

The server must also be configured with a *key store* and an optional *trust store*.

These are both *Java keystores* which can be managed using the [keytool](http://docs.oracle.com/javase/6/docs/technotes/tools/solaris/keytool.html) utility which ships with the JDK.

The keytool command allows you to create keystores, and import and export certificates from them.

The key store should contain the server certificate. This is mandatory - the client will not be able to connect to the server over SSL if the server does not have a certificate.

The key store is configured on the server using the attributes `key_store_path` and `key_store_password`.

The trust store is optional and contains the certificates of any clients it should trust. This is only used if client authentication is required.

To configure a server to use server certificates only:

    server = Vertx::NetServer.new
    server.ssl = true
    server.key_store_path = '/path/to/your/keystore/server-keystore.jks'
    server.key_store_password = 'password'

Making sure that `server-keystore.jks` contains the server certificate.

To configure a server to also require client certificates:

    server = Vertx::NetServer.new
    server.ssl = true
    server.key_store_path = '/path/to/your/keystore/server-keystore.jks'
    server.key_store_password = 'password'
    server.client_auth_required = true
    server.trust_store_path = '/path/to/your/truststore/server-truststore.jks'
    server.trust_store_password = 'password'

Making sure that `server-truststore.jks` contains the certificates of any clients who the server trusts.

If the attribute `client_auth_required` is set to `true` and the client cannot provide a certificate, or it provides a certificate that the server does not trust then the connection attempt will not succeed.

## SSL Clients

Net Clients can also be easily configured to use SSL. They have the exact same API when using SSL as when using standard sockets.

To enable SSL on a `NetClient` the attribute `ssl` must be set to `true`.

If the attribute `trust_all` has been set to `true`, then the client will trust all server certificates. The connection will still be encrypted but this mode is vulnerable to 'man in the middle' attacks. I.e. you can't be sure who you are connecting to. Use this with caution. Default value is `false`.

If `trust_all` is set to `false`, then a client trust store must be configured and should contain the certificates of the servers that the client trusts.

The default value of `trust_all` is `false`.

The client trust store is just a standard Java key store, the same as the key stores on the server side. The client trust store location is set by setting the attribute `trust_store_path` on the `NetClient`. If a server presents a certificate during connection which is not in the client trust store, the connection attempt will not succeed.

If the server requires client authentication then the client must present its own certificate to the server when connecting. This certificate should reside in the client key store. Again it's just a regular Java key store. The client keystore location is set with the attribute `key_store_path` on the `NetClient`.

To configure a client to trust all server certificates (dangerous):

    client = Vertx::NetClient.new
    client.ssl = true
    client.trust_all = true

To configure a client to only trust those certificates it has in its trust store:

    client = Vertx::NetClient.new
    client.ssl = true
    client.trust_store_path = '/path/to/your/client/truststore/client-truststore.jks'
    client.trust_store_password = 'password'

To configure a client to only trust those certificates it has in its trust store, and also to supply a client certificate:

    client = Vertx::NetClient.new
    client.ssl = true
    client.trust_store_path = '/path/to/your/client/truststore/client-truststore.jks'
    client.trust_store_password = 'password'
    client.key_store_path = '/path/to/keystore/holding/client/cert/client-keystore.jks'
    client.key_store_password = 'password'    

# Flow Control - Streams and Pumps

There are several objects in vert.x that allow data to be read from and written to in the form of Buffers.

All operations in the vert.x API are non blocking; calls to write data return immediately and writes are internally queued.

It's not hard to see that if you write to an object faster than it can actually write the data to its underlying resource then the write queue could grow without bound - eventually resulting in exhausting available memory.

To solve this problem a simple flow control capability is provided by some objects in the vert.x API.

Any flow control aware object that can be written to is said to implement `ReadStream`, and any flow control object that can be read from is said to implement `WriteStream`.

Let's take an example where we want to read from a `ReadStream` and write the data to a `WriteStream`.

A very simple example would be reading from a `NetSocket` on a server and writing back to the same `NetSocket` - since `NetSocket` implements both `ReadStream` and `WriteStream`, but you can do this between any `ReadStream` and any `WriteStream`, including HTTP requests and response, async files, WebSockets, etc.

A naive way to do this would be to directly take the data that's been read and immediately write it to the NetSocket, for example:

    server = Vertx::NetServer.new

    server.connect_handler do |sock|

        sock.data_handler do |buffer|

            # Write the data straight back

            sock.write(buffer)
        end
       
    end.listen(1234, 'localhost')

There's a problem with the above example: If data is read from the socket faster than it can be written back to the socket, it will build up in the write queue of the AsyncFile, eventually running out of RAM. This might happen, for example if the client at the other end of the socket wasn't reading very fast, effectively putting back-pressure on the connection.

Since `NetSocket` implements `WriteStream`, we can check if the `WriteStream` is full before writing to it:

    server = Vertx::NetServer.new

    server.connect_handler do |sock|

        sock.data_handler do |buffer|

            sock.write(buffer) if !sock.write_queue_full?
        end
       
    end.listen(1234, 'localhost')

This example won't run out of RAM but we'll end up losing data if the write queue gets full. What we really want to do is pause the `NetSocket` when the `AsyncFile` write queue is full. Let's do that:

    server = Vertx::NetServer.new

    server.connect_handler do |sock|

        sock.data_handler do |buffer|

            if sock.write_queue_full?
                sock.pause
            else
              sock.write(buffer)
            end
            
        end
       
    end.listen(1234, 'localhost')

We're almost there, but not quite. The `NetSocket` now gets paused when the file is full, but we also need to *unpause* it when the file write queue has processed its backlog:

    server = Vertx::NetServer.new

    server.connect_handler do |sock|

        sock.data_handler do |buffer|

            if sock.write_queue_full?
                sock.pause
                sock.drain_handler { sock.resume }
            else
                sock.write(buffer)
            end
            
        end
       
    end.listen(1234, 'localhost')

And there we have it. The `drain_handler` event handler will get called when the write queue is ready to accept more data, this resumes the `NetSocket` which allows it to read more data.

It's very common to want to do this when writing vert.x applications, so we provide a helper class called `Pump` which does all this hard work for you. You just feed it the `ReadStream` and the `WriteStream` and it tell it to start:

    server = Vertx::NetServer.new

    server.connect_handler do |sock|

        pump = Vertx::Pump.new(sock, sock)
        pump.start
        
    end.listen(1234, 'localhost')

Which does exactly the same thing as the more verbose example.

Let's look at the methods on `ReadStream` and `WriteStream` in more detail:

## ReadStream

`ReadStream` is implemented by `AsyncFile`, `HttpClientResponse`, `HttpServerRequest`, `WebSocket`, `NetSocket` and `SockJSSocket`.

Functions:

* `data_handler`: set a handler which will receive data from the `ReadStream`. As data arrives the handler will be passed a Buffer.
* `pause`: pause the handler. When paused no data will be received in the `data_handler`.
* `resume`: resume the handler. The handler will be called if any data arrives.
* `exception_handler`: Will be called if an exception occurs on the `ReadStream`.
* `end_handler`: Will be called when end of stream is reached. This might be when EOF is reached if the `ReadStream` represents a file, or when end of request is reached if it's an HTTP request, or when the connection is closed if it's a TCP socket.

## WriteStream

`WriteStream` is implemented by `AsyncFile`, `HttpClientRequest`, `HttpServerResponse`, `WebSocket`, `NetSocket` and `SockJSSocket`

Functions:

* `write_buffer`: write a Buffer to the `WriteStream`. This method will never block. Writes are queued internally and asynchronously written to the underlying resource.
* `write_queue_max_size=`: set the number of bytes at which the write queue is considered *full*, and the function `write_queue_full?` returns `true`. Note that, even if the write queue is considered full, if `writeBuffer` is called the data will still be accepted and queued.
* `write_queue_full?`: returns `true` if the write queue is considered full.
* `exception_handler`: Will be called if an exception occurs on the `WriteStream`.
* `drain_handler`: The handler will be called if the `WriteStream` is considered no longer full.

## Pump

Instances of `Pump` have the following methods:

* `start`. Start the pump.
* `stop`. Stops the pump. When the pump starts it is in stopped mode.
* `write_queue_max_size=`. This has the same meaning as `write_queue_max_size=` on the `WriteStream`.
* `bytes_pumped`. Returns total number of bytes pumped.

A pump can be started and stopped multiple times.

# Writing HTTP Servers and Clients

## Writing HTTP servers

Vert.x allows you to easily write full featured, highly performant and scalable HTTP servers.

### Creating an HTTP Server

To create an HTTP server you simply create an instance of `vertx.net.HttpServer`.

    server = Vertx::HttpServer.new

### Start the Server Listening

To tell that server to listen for incoming requests you use the `listen` method:

    server = Vertx::HttpServer.new

    server.listen(8080, 'myhost')

The first parameter to `listen` is the port. The second parameter is the hostname or ip address. If the hostname is omitted it will default to `0.0.0.0` which means it will listen at all available interfaces.


### Getting Notified of Incoming Requests

To be notified when a request arrives you need to set a request handler. This is done by calling the `request_handler` method of the server, passing in the handler:

    server = Vertx::HttpServer.new

    server.request_handler do |request|
      puts 'An HTTP request has been received'
    end

    server.listen(8080, 'localhost')

This displays 'An HTTP request has been received!' every time an HTTP request arrives on the server. You can try it by running the verticle and pointing your browser at `http://localhost:8080`.

Similarly to `NetServer`, the return value of the `request_handler` function is the server itself, so multiple invocations can be chained together. That means we can rewrite the above with:

    server = Vertx::HttpServer.new

    server.request_handler do |request|
      puts 'An HTTP request has been received'
    end.listen(8080, 'localhost')

Or:

    server = Vertx::HttpServer.new.request_handler do |request|
      puts 'An HTTP request has been received'
    end.listen(8080, 'localhost')


### Handling HTTP Requests

So far we have seen how to create an `HttpServer` and be notified of requests. Lets take a look at how to handle the requests and do something useful with them.

When a request arrives, the request handler is called passing in an instance of `HttpServerRequest`. This object represents the server side HTTP request.

The handler is called when the headers of the request have been fully read. If the request contains a body, that body may arrive at the server some time after the request handler has been called.

It contains functions to get the URI, path, request headers and request parameters. It also contains a `response` property which is a reference to an object that represents the server side HTTP response for the object.

#### Request Method

The request object has a property `method` which is a string representing what HTTP method was requested. Possible values for `method` are: `GET`, `PUT`, `POST`, `DELETE`, `HEAD`, `OPTIONS`, `CONNECT`, `TRACE`, `PATCH`.

#### Request URI

The request object has a property `uri` which contains the full URI (Uniform Resource Locator) of the request. For example, if the request URI was:

    /a/b/c/page.html?param1=abc&param2=xyz    
    
Then `request.uri` would contain the string `/a/b/c/page.html?param1=abc&param2=xyz`.

Request URIs can be relative or absolute (with a domain) depending on what the client sent. In many cases they will be relative.

The request uri contains the value as defined in [Section 5.1.2 of the HTTP specification - Request-URI](http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html)

#### Request Path

The request object has a property `path` which contains the path of the request. For example, if the request URI was:

    /a/b/c/page.html?param1=abc&param2=xyz

Then `request.path` would contain the string `/a/b/c/page.html`

#### Request Query

The request object has a property `query` which contains the query of the request. For example, if the request URI was:

    /a/b/c/page.html?param1=abc&param2=xyz

Then `request.query` would contain the string `param1=abc&param2=xyz`

#### Request Headers

The request headers are available using the `headers` method on the request object. The return value of the method is a Ruby Hash.

Note that the header keys are always lower-cased before being returned to you.

Here's an example that echoes the headers to the output of the response. Run it and point your browser at `http://localhost:8080` to see the headers.

    server.request_handler do |request|

      request.response.put_header('Content-Type', 'text/plain')
         
      str = "Headers are\n"
      request.headers.each do |key, value|
        str << "#{key}: #{value}\n"
      end
      
      request.response.end(str)
      
    end.listen(8080, 'localhost')

#### Request params

Similarly to the headers, the request parameters are available using the `params` method on the request object. The return value of the function is just a Hash too.

Request parameters are sent on the request URI, after the path. For example if the URI was:

    /page.html?param1=abc&param2=xyz

Then the params hash would be the following JS object:

    { param1: 'abc', param2: 'xyz' }

#### Reading Data from the Request Body

Sometimes an HTTP request contains a request body that we want to read. As previously mentioned the request handler is called when only the headers of the request have arrived so the `HttpServerRequest` object does not contain the body. This is because the body may be very large and we don't want to create problems with exceeding available memory.

To receive the body, you set the `data_handler` on the request object. This will then get called every time a chunk of the request body arrives. Here's an example:

    server.request_handler do |request|

      request.data_handler do |buffer|
        puts "I received  #{buffer.length} bytes"
      end
      
    end.listen(8080, 'localhost')


The `data_handler` may be called more than once depending on the size of the body.

You'll notice this is very similar to how data from `NetSocket` is read.

The request object implements the `ReadStream` interface so you can pump the request body to a `WriteStream`. See the chapter on streams and pumps for a detailed explanation.

In many cases, you know the body is not large and you just want to receive it in one go. To do this you could do something like the following:

    server = Vertx::HttpServer.new

    server.request_handler do |request|

      # Create a buffer to hold the body
      body = Vertx::Buffer.create(0)

      request.data_handler do |buffer|
        # Append the chunk to the buffer
        body.append_buffer(buffer)
      end

      request.end_handler do
        # The entire body has now been received
        puts "The total body received was #{body.length} bytes"
      end

    end.listen(8080, 'localhost')

Like any `ReadStream` the end handler is invoked when the end of stream is reached - in this case at the end of the request.

If the HTTP request is using HTTP chunking, then each HTTP chunk of the request body will correspond to a single call of the data handler.

It's a very common use case to want to read the entire body before processing it, so vert.x allows a `body_handler` to be set on the request object.

The body handler is called only once when the *entire* request body has been read.

*Beware of doing this with very large requests since the entire request body will be stored in memory.*

Here's an example using `body_handler`:

    server = Vertx::HttpServer.new

    server.request_handler do |request|

      request.body_handler do |body|
        puts "The total body received was #{body.length} bytes"
      end

    end.listen(8080, 'localhost')

Simples, innit?

### HTTP Server Responses

As previously mentioned, the HTTP request object contains a property `response`. This is the HTTP response for the request. You use it to write the response back to the client.

### Setting Status Code and Message

To set the HTTP status code for the response use the `status_code` property. You can also use the `status_message` property to set the status message. If you do not set the status message a default message will be used.

    server = Vertx::HttpServer.new

    server.request_handler do |request|

      request.response.status_code = 404
      request.response.status_message = "Too many gerbils"
      request.response.end

    end.listen(8080, 'localhost')
    
The default value for `status_code` is `200`.    

#### Writing HTTP responses

To write data to an HTTP response, you invoke the `write_buffer` or `write_str` methods. These methods can be invoked multiple times before the response is ended.

With a single buffer:

    myBuffer = ...
    request.response.write_buffer(myBuffer)

A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    request.response.write_str('hello')

A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.

    request.response.write_str('hello', 'UTF-16')

The write methods are asynchronous and always returns immediately after the write has been queued.

The actual write might complete some time later. If you want to be informed when the actual write has completed you can specify a block when calling the method. The block will then be invoked when the write has completed:

    request.response.write_str('hello') { puts 'It has actually been written' }

If you are just writing a single string or Buffer to the HTTP response you can write it and end the response in a single call to the `end` function.

The first call to write results in the response header being being written to the response.

Consequently, if you are not using HTTP chunking then you must set the `Content-Length` header before writing to the response, since it will be too late otherwise. If you are using HTTP chunking you do not have to worry.

#### Ending HTTP responses

Once you have finished with the HTTP response you must call the `end` method on it.

This method can be invoked in several ways:

With no arguments, the response is simply ended.

    request.response.end

The function can also be called with a string or Buffer in the same way write is called. In this case it's just the same as calling write with a string or Buffer followed by calling `end` with no arguments. For example:

    request.response.end('That's all folks')

#### Closing the underlying connection

You can close the underlying TCP connection of the request by calling the `close` method.

    request.response.close

#### Response headers

HTTP response headers can be added to the response by adding them to the Hash returned from the `headers` method:

    request.response.headers['Some-header'] = 'foo'

Also, individual HTTP response headers can be written using the `put_header` function, allowing a more fluent API. For example:

    request.response.put_header('Some-Other-Header', 'wibble').
                     put_header('Blah', 'Eek')

Response headers must all be added before any parts of the response body are written.

#### Chunked HTTP Responses and Trailers

Vert.x supports [HTTP Chunked Transfer Encoding](http://en.wikipedia.org/wiki/Chunked_transfer_encoding). This allows the HTTP response body to be written in chunks, and is normally used when a large response body is being streamed to a client, whose size is not known in advance.

You put the HTTP response into chunked mode by setting the `chunked` property.

    req.response.chunked = true

Default is non-chunked. When in chunked mode, each call to `response.write_buffer` or `response.write_str` will result in a new HTTP chunk being written out.

When in chunked mode you can also write HTTP response trailers to the response. These are actually written in the final chunk of the response.

Like headers, HTTP response trailers can be added to the response by adding them to the Hash returned from the `trailers` method:

    request.response.trailers['Some-header'] = 'foo'

Also, individual HTTP response trailers can be written using the `put_trailer` method, allowing a more fluent API. For example:

    request.response.put_trailer('Some-Other-Header', 'wibble').
                     put_trailer('Blah', 'Eek')


### Serving files directly from disk

If you were writing a web server, one way to serve a file from disk would be to open it as an `AsyncFile` and pump it to the HTTP response. Or you could load it it one go using the file system API and write that to the HTTP response.

Alternatively, vert.x provides a method which allows you to send serve a file from disk to HTTP response in one operation. Where supported by the underlying operating system this may result in the OS directly transferring bytes from the file to the socket without being copied through userspace at all.

Using `send_file` is usually more efficient for large files, but may be slower than using `readFile` to manually read the file as a buffer and write it directly to the response.

To do this use the `send_file` function on the HTTP response. Here's a simple HTTP web server that serves static files from the local `web` directory:

    server = Vertx::HttpServer.new

    server.request_handler do |req|
      file = ''
      if req.path == '/'
        file = 'index.html'
      elsif !req.path.include?('..')
        file = req.path
      end
      req.response.send_file('web/' + file)
    end.listen(8080, 'localhost')

*Note: If you use `send_file` while using HTTPS it will copy through userspace, since if the kernel is copying data directly from disk to socket it doesn't give us an opportunity to apply any encryption.*

**If you're going to write web servers using vert.x be careful that users cannot exploit the path to access files outside the directory from which you want to serve them.**

### Pumping Responses

Since the HTTP Response implements `WriteStream` you can pump to it from any `ReadStream`, e.g. an `AsyncFile`, `NetSocket` or `HttpServerRequest`.

Here's an example which echoes HttpRequest headers and body back in the HttpResponse. It uses a pump for the body, so it will work even if the HTTP request body is much larger than can fit in memory at any one time:

    server = Vertx::HttpServer.new

    server.request_handler do |req|

      req.response.put_headers(req.headers)

      p = Pump.new(req, req.response)
      p.start
      
      req.end_handler { req.response.end }

    end.listen(8080, 'localhost')

## Writing HTTP Clients

### Creating an HTTP Client

To create an HTTP client you simply create an instance of `vertx.HttpClient`:

    client = Vertx::HttpClient.new

You set the port and hostname (or ip address) that the client will connect to using the `setHost` and `setPort` functions:

    client = Vertx::HttpClient.new
    client.port = 8181
    client.host = 'foo.com'

A single `HttpClient` always connects to the same host and port. If you want to connect to different servers, create more instances.

The default port is `80` and the default host is `localhost`. So if you don't explicitly set these values that's what the client will attempt to connect to.

### Pooling and Keep Alive

By default the `HttpClient` pools HTTP connections. As you make requests a connection is borrowed from the pool and returned when the HTTP response has ended.

If you do not want connections to be pooled you can call `setKeepAlive` with `false`:

    client = Vertx::HttpClient.new    
    client.keep_alive = false

In this case a new connection will be created for each HTTP request and closed once the response has ended.

You can set the maximum number of connections that the client will pool as follows:

    client = Vertx::HttpClient.new    
    client.max_pool_size = 10

The default value is `1`.

### Closing the client

Vert.x will automatically close any clients when the verticle is stopped, but if you want to close it explicitly you can:

    client.close

### Making Requests

To make a request using the client you invoke one the methods named after the HTTP method that you want to invoke.

For example, to make a `POST` request:

    client = Vertx::HttpClient.new
    client.host = 'foo.com'  

    request = client.post('/some-path/') do |resp|
        puts "got response #{resp.status_code}" 
    end

    request.end

To make a PUT request use the `put` method, to make a GET request use the `get` method, etc.

Legal request methods are: `get`, `put`, `post`, `delete`, `head`, `options`, `connect`, `trace` and `patch`.

The general modus operandi is you invoke the appropriate method passing in the request URI as the first parameter, the second parameter is an event handler which will get called when the corresponding response arrives. The response handler is passed the client response object as an argument.

The value specified in the request URI corresponds to the Request-URI as specified in [Section 5.1.2 of the HTTP specification](http://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html). In most cases it will be a relative URI.

*Please note that the domain/port that the client connects to is determined by `setPort` and `setHost`, and is not parsed from the uri.*

The return value from the appropriate request method is an `HttpClientRequest` object. You can use this to add headers to the request, and to write to the request body. The request object implements `WriteStream`.

Once you have finished with the request you must call the `end` method.

If you don't know the name of the request method in advance there is a general `request` method which takes the HTTP method as a parameter:

    client = Vertx::HttpClient.new
    client.host = 'foo.com'    

    request = client.request('POST', '/some-path/') do |resp|
        puts "got response #{resp.status_code}" 
    end

    request.end

There is also a method called `get_now` which does the same as `get`, but automatically ends the request. This is useful for simple GETs which don't have a request body:

    client = Vertx::HttpClient.new
    client.host = 'foo.com'    

    client.get_now('/some-path/') do |resp|
        puts "got response #{resp.status_code}" 
    end

With `get_now` there is no return value.

#### Writing to the request body

Writing to the client request body has a very similar API to writing to the server response body.

To write data to an `HttpClientRequest` object, you invoke the `write_buffer` or `write_str` methods. These methods can be called multiple times before the request has ended.

With a single buffer:

    myBuffer = ...
    request.write_buffer(myBuffer)

A string. In this case the string will encoded using UTF-8 and the result written to the wire.

    request.write_str('hello')

A string and an encoding. In this case the string will encoded using the specified encoding and the result written to the wire.

    request.write_str('hello', 'UTF-16')

The write functions are asynchronous and always return immediately after the write has been queued. The actual write might complete some time later.

If you want to be informed when the actual write has completed you specify a block to the method. This block will be invoked when the write has completed:

    request.response.write('hello') { puts 'It has actually been written' }

If you are just writing a single string or Buffer to the HTTP request you can write it and end the request in a single call to the `end` method.

The first call to `write` results in the request header being being written to the request.

Consequently, if you are not using HTTP chunking then you must set the `Content-Length` header before writing to the request, since it will be too late otherwise. If you are using HTTP chunking you do not have to worry.


#### Ending HTTP requests

Once you have finished with the HTTP request you must call the `end` method on it.

This method can be invoked in several ways:

With no arguments, the request is simply ended.

    request.end

The method can also be called with a string or Buffer in the same way write is called. In this case it's just the same as calling write with a string or Buffer followed by calling `end` with no arguments.

#### Writing Request Headers

To write headers to the request, add them to the Hash returned from the `headers` method:

    client = Vertx::HttpClient.new
    client.host = 'foo.com'  

    request = client.post('/some-path') do |resp|
        puts "got response #{resp.status_code}" 
    end

    request.headers['Some-Header'] = 'Some-Value'
    request.end
    
You can also use the `put_header` method to enable a more fluent API:    

    client = Vertx::HttpClient.new
    client.host = 'foo.com'  

    request = client.post('/some-path') do |resp|
        puts "got response #{resp.status_code}" 
    end.put_header('Some-Header', 'Some-Value').
        put_header('Some-Other-Header', 'Some-Other-Value').
        end

#### HTTP chunked requests

Vert.x supports [HTTP Chunked Transfer Encoding](http://en.wikipedia.org/wiki/Chunked_transfer_encoding) for requests. This allows the HTTP request body to be written in chunks, and is normally used when a large request body is being streamed to the server, whose size is not known in advance.

You put the HTTP request into chunked mode by setting the attribute `chunked`.

    request.chunked = true

Default is non-chunked. When in chunked mode, each call to `request.write_str` or `request.write_buffer` will result in a new HTTP chunk being written out.

### HTTP Client Responses

Client responses are received as an argument to the response handler block that is specified when making a request.

The response object implements `ReadStream`, so it can be pumped to a `WriteStream` like any other `ReadStream`.

To query the status code of the response use the `status_code` property. The `status_message` property contains the status message. For example:

    client = Vertx::HttpClient.new
    client.host = 'foo.com'  

    client.get_now('/some-path') do |resp|
      puts "server returned status code: #{resp.status_code}"
      puts "server returned status message: #{resp.status_message}"      
    end

#### Reading Data from the Response Body

The API for reading a http client response body is very similar to the API for reading a http server request body.

Sometimes an HTTP response contains a request body that we want to read. Like an HTTP request, the client response handler is called when all the response headers have arrived, not when the entire response body has arrived.

To receive the response body, you set a `dataHandler` on the response object which gets called as parts of the HTTP response arrive. Here's an example:


    client = Vertx::HttpClient.new
    client.host = 'foo.com'

    client.get_now('/some-path') do |resp|
      resp.data_handler { |buffer| puts "I received #{buffer.length} bytes" }     
    end


The response object implements the `ReadStream` interface so you can pump the response body to a `WriteStream`. See the chapter on streams and pump for a detailed explanation.

The `data_handler` can be called multiple times for a single HTTP response.

As with a server request, if you wanted to read the entire response body before doing something with it you could do something like the following:

    client = Vertx::HttpClient.new
    client.host = 'foo.com'  

    client.get_now('/some-path') do |resp|

      # Create a buffer to hold the entire response body
      body = Vertx::Buffer.create(0)

      resp.data_handler do |buffer|
        # Add chunk to the buffer
        body.append_buffer(buffer)
      end

      resp.end_handler do
        # The entire response body has been received
        puts "The total body received was #{body.length} bytes"
      end

    end

Like any `ReadStream` the end handler is invoked when the end of stream is reached - in this case at the end of the response.

If the HTTP response is using HTTP chunking, then each chunk of the response body will correspond to a single call to the `data_handler`.

It's a very common use case to want to read the entire body in one go, so vert.x allows a `body_handler` to be set on the response object.

The body handler is called only once when the *entire* response body has been read.

*Beware of doing this with very large responses since the entire response body will be stored in memory.*

Here's an example using `body_handler`:

    client = Vertx::HttpClient.new
    client.host = 'foo.com'  

    client.get_now('/some-path') do |resp|

      resp.body_handler do |body|
        puts "The total body received was #{body.length} bytes"
      end

    end

## Pumping Requests and Responses

The HTTP client and server requests and responses all implement either `ReadStream` or `WriteStream`. This means you can pump between them and any other read and write streams.

### 100-Continue Handling

According to the [HTTP 1.1 specification](http://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html) a client can set a header `Expect: 100-Continue` and send the request header before sending the rest of the request body.

The server can then respond with an interim response status `Status: 100 (Continue)` to signify the client is ok to send the rest of the body.

The idea here is it allows the server to authorise and accept/reject the request before large amounts of data is sent. Sending large amounts of data if the request might not be accepted is a waste of bandwidth and ties up the server in reading data that it will just discard.

Vert.x allows you to set a `continue_handler` on the client request object. This will be called if the server sends back a `Status: 100 (Continue)` response to signify it is ok to send the rest of the request.

This is used in conjunction with the `send_head` function to send the head of the request.

An example will illustrate this:

    client = Vertx::HttpClient.new
    client.host = 'foo.com'  

    request = client.put('/some-path') do |resp|
    
      puts "Got a response: #{resp.status_code}"     

    end

    request.put_header('Expect', '100-Continue')
    request.chunked = true

    request.continue_handler do
        # OK to send rest of body
        
        request.write_str('Some data').end
    end

    request.send_head

## HTTPS Servers

HTTPS servers are very easy to write using vert.x.

An HTTPS server has an identical API to a standard HTTP server. Getting the server to use HTTPS is just a matter of configuring the HTTP Server before `listen` is called.

Configuration of an HTTPS server is done in exactly the same way as configuring a `NetServer` for SSL. Please see the SSL server chapter for detailed instructions.

## HTTPS Clients

HTTPS clients can also be very easily written with vert.x

Configuring an HTTP client for HTTPS is done in exactly the same way as configuring a `NetClient` for SSL. Please see the SSL client chapter for detailed instructions.

## Scaling HTTP servers

Scaling an HTTP or HTTPS server over multiple cores is as simple as deploying more instances of the verticle. For example:

    vertx deploy http_server.rb -instances 20

The scaling works in the same way as scaling a `NetServer`. Please see the chapter on scaling Net Servers for a detailed explanation of how this works.

# Routing HTTP requests with Pattern Matching

Vert.x lets you route HTTP requests to different handlers based on pattern matching on the request path. It also enables you to extract values from the path and use them as parameters in the request.

This is particularly useful when developing REST-style web applications.

To do this you simply create an instance of `Vertx::RouteMatcher` and use it as handler in an HTTP server. See the chapter on HTTP servers for more information on setting HTTP handlers. Here's an example:

    server = Vertx::HttpServer.new

    route_matcher = Vertx::RouteMatcher.new

    server.request_Handler(route_matcher).listen(8080, 'localhost')

## Specifying matches.

You can then add different matches to the route matcher. For example, to send all GET requests with path `/animals/dogs` to one handler and all GET requests with path `/animals/cats` to another handler you would do:

    server = Vertx::HttpServer.new

    route_matcher = Vertx::RouteMatcher.new

    route_matcher.get('/animals/dogs') do |req|
        req.response.end('You requested dogs')
    end

    route_matcher.get('/animals/cats') do |req|
        req.response.end('You requested cats')
    end

    server.request_handler(route_matcher).listen(8080, 'localhost')

Corresponding methods exist for each HTTP method - `get`, `post`, `put`, `delete`, `head`, `options`, `trace`, `connect` and `patch`.

There's also an `all` method which applies the match to any HTTP request method.

The handler specified to the method is just a normal HTTP server request handler, the same as you would supply to the `request_handler` method of the HTTP server.

You can provide as many matches as you like and they are evaluated in the order you added them, the first matching one will receive the request.

A request is sent to at most one handler.

## Extracting parameters from the path

If you want to extract parameters from the path, you can do this too, by using the `:` (colon) character to denote the name of a parameter. For example:

    server = Vertx::HttpServer.new

    route_matcher = Vertx::RouteMatcher.new

    route_matcher.put('/:blogname/:post') do |req|
        blogName = req.params['blogname']
        post = req.params['post']
        req.response.end("blogname is #{blogName} post is #{post}")
    end

    server.request_handler(route_matcher).listen(8080, 'localhost')

Any params extracted by pattern matching are added to the map of request parameters.

In the above example, a PUT request to `/myblog/post1` would result in the variable `blogName` getting the value `myblog` and the variable `post` getting the value `post1`.

Valid parameter names must start with a letter of the alphabet and be followed by any letters of the alphabet or digits.

## Extracting params using Regular Expressions

Regular Expressions can be used to extract more complex matches. In this case capture groups are used to capture any parameters.

Since the capture groups are not named they are added to the request with names `param0`, `param1`, `param2`, etc.

Corresponding methods exist for each HTTP method - `get_re`, `post_re`, `put_re`, `delete_re`, `head_re`, `options_re`, `trace_re`, `connect_re` and `patch_re`.

There's also an `all_re` method which applies the match to any HTTP request method.

For example:

    server = Vertx::HttpServer.new

    route_matcher = Vertx::RouteMatcher.new

    route_matcher.all_re("\/([^\/]+)\/([^\/]+)") do |req|
        first = req.params['param0']
        second = req.params['param1']
        req.response.end("first is #{first} second is #{second}")
    end

    server.request_handler(route_matcher).listen(8080, 'localhost')
    
Run the above and point your browser at `http://localhost:8080/animals/cats`.

It will display 'first is animals and second is cats'.    

## Handling requests where nothing matches

You can use the `no_match` method to specify a handler that will be called if nothing matches. If you don't specify a no match handler and nothing matches, a 404 will be returned.

    route_matcher.no_match { |req| req.response.end('Nothing matched') }

# WebSockets

[WebSockets](http://en.wikipedia.org/wiki/WebSocket) are a feature of HTML 5 that allows a full duplex socket-like connection between HTTP servers and HTTP clients (typically browsers).

## WebSockets on the server

To use WebSockets on the server you create an HTTP server as normal, but instead of setting a `request_handler` you set a `websocket_handler` on the server.

    server = Vertx::HttpServer.new

    server.websocket_handler do |ws|

      # A WebSocket has connected!

    end.listen(8080, 'localhost')

### Reading from and Writing to WebSockets

The `websocket` instance passed into the handler implements both `ReadStream` and `WriteStream`, so you can read and write data to it in the normal ways. I.e by setting a `data_handler` and calling the `write_buffer` and `write_str` methods.

See the chapter on `NetSocket` and streams and pumps for more information.

For example, to echo all data received on a WebSocket:

    server = Vertx::HttpServer.new

    server.websocket_handler do |websocket|

      p = new Pump(websocket, websocket)
      p.start

    end.listen(8080, 'localhost')

The `websocket` instance also has method `write_binary_frame` for writing binary data. This has the same effect as calling `write_buffer`.

Another method `write_text_frame` also exists for writing text data. This is equivalent to calling

    websocket.write_buffer(Vertx::Buffer.create('some-string'))

### Rejecting WebSockets

Sometimes you may only want to accept WebSockets which connect at a specific path.

To check the path, you can query the `path` property of the `websocket`. You can then call the `reject` method to reject the websocket.

    server = Vertx::HttpServer.new

    server.websocket_handler do |websocket|

      if websocket.path == '/services/echo'
        p = Vertx::Pump.new(websocket, websocket)
        p.start
      else
        websocket.reject
      end
    end.listen(8080, 'localhost')

## WebSockets on the HTTP client

To use WebSockets from the HTTP client, you create the HTTP client as normal, then call the `connect_websocket` function, passing in the URI that you wish to connect to at the server, and a handler.

The handler will then get called if the WebSocket successfully connects. If the WebSocket does not connect - perhaps the server rejects it, then any exception handler on the HTTP client will be called.

Here's an example of WebSockets on the client:

    client = Vertx::HttpClient.new
    client.port = 8080

    client.connect_web_socket('http://localhost:8080/services/echo') do |websocket|

      websocket.data_handler { |buff| puts "got #{buff}"}
      
      websocket.write_text_frame('foo')
      
    end

Again, the client side WebSocket implements `ReadStream` and `WriteStream`, so you can read and write to it in the same way as any other stream object.

## WebSockets in the browser

To use WebSockets from a compliant browser, you use the standard WebSocket API. Here's some example client side JavaScript which uses a WebSocket.

    <script>

        var socket = new WebSocket("ws://localhost:8080/services/echo");

        socket.onmessage = function(event) {
            alert("Received data from websocket: " + event.data);
        }

        socket.onopen = function(event) {
            alert("Web Socket opened");
            socket.send("Hello World");
        };

        socket.onclose = function(event) {
            alert("Web Socket closed");
        };

    </script>

For more information see the [WebSocket API documentation](http://dev.w3.org/html5/websockets/)

## Routing WebSockets with Pattern Matching

**TODO**

# SockJS

WebSockets are a new technology, and many users are still using browsers that do not support them, or which support older, pre-final, versions.

Moreover, WebSockets do not work well with many corporate proxies. This means that's it's not possible to guarantee a WebSocket connection is going to succeed for every user.

Enter SockJS.

SockJS is a client side JavaScript library and protocol which provides a simple WebSocket-like interface to the client side JavaScript developer irrespective of whether the actual browser or network will allow real WebSockets.

It does this by supporting various different transports between browser and server, and choosing one at runtime according to browser and network capabilities. All this is transparent to you - you are simply presented with the WebSocket-like interface which *just works*.

Please see the [SockJS website](https://github.com/sockjs/sockjs-client) for more information.

## SockJS Server

Vert.x provides a complete server side SockJS implementation.

This enables vert.x to be used for modern, so-called *real-time* (this is the *modern* meaning of *real-time*, not to be confused by the more formal pre-existing definitions of soft and hard real-time systems) web applications that push data to and from rich client-side JavaScript applications, without having to worry about the details of the transport.

To create a SockJS server you simply create a HTTP server as normal and pass it in to the constructor of the SockJS server.

    httpServer = Vertx::HttpServer.new

    sockJSServer = Vertx::SockJSServer.new(httpServer)

Each SockJS server can host multiple *applications*.

Each application is defined by some configuration, and provides a handler which gets called when incoming SockJS connections arrive at the server.

For example, to create a SockJS echo application:

    httpServer = Vertx::HttpServer.new

    sockJSServer = Vertx::SockJSServer.new(httpServer)

    config = { 'prefix' => '/echo' }

    sockJSServer.install_app(config) do |sock|

        p = Vertx::Pump.new(sock, sock)

        p.start
    end

    httpServer.listen(8080)

The configuration can take the following fields:

* `prefix`: A url prefix for the application. All http requests whose paths begins with selected prefix will be handled by the application. This property is mandatory.
* `insert_JSESSIONID`: Some hosting providers enable sticky sessions only to requests that have JSESSIONID cookie set. This setting controls if the server should set this cookie to a dummy value. By default setting JSESSIONID cookie is enabled. More sophisticated beaviour can be achieved by supplying a function.
* `session_timeout`: The server sends a `close` event when a client receiving connection have not been seen for a while. This delay is configured by this setting. By default the `close` event will be emitted when a receiving connection wasn't seen for 5 seconds.
* `heartbeat_period`: In order to keep proxies and load balancers from closing long running http requests we need to pretend that the connecion is active and send a heartbeat packet once in a while. This setting controlls how often this is done. By default a heartbeat packet is sent every 25 seconds.
* `max_bytes_streaming`: Most streaming transports save responses on the client side and don't free memory used by delivered messages. Such transports need to be garbage-collected once in a while. `max_bytes_streaming` sets a minimum number of bytes that can be send over a single http streaming request before it will be closed. After that client needs to open new request. Setting this value to one effectively disables streaming and will make streaming transports to behave like polling transports. The default value is 128K.
* `library_url`: Transports which don't support cross-domain communication natively ('eventsource' to name one) use an iframe trick. A simple page is served from the SockJS server (using its foreign domain) and is placed in an invisible iframe. Code run from this iframe doesn't need to worry about cross-domain issues, as it's being run from domain local to the SockJS server. This iframe also does need to load SockJS javascript client library, and this option lets you specify its url (if you're unsure, point it to the latest minified SockJS client release, this is the default). The default value is `http://cdn.sockjs.org/sockjs-0.1.min.js`

## Reading and writing data from a SockJS server

The object passed into the SockJS handler implements `ReadStream` and `WriteStream` much like `NetSocket` or `WebSocket`. You can therefore use the standard API for reading and writing to the SockJS socket or using it in pumps. See the chapter on Streams and Pumps for more information.

    httpServer = Vertx::HttpServer.new

    sockJSServer = Vertx::SockJSServer.new(httpServer)

    config = { 'prefix' => '/echo' }

    sockJSServer.install_app(config) do |sock|

        sock.data_handler do |buffer|
            sock.write_buffer(buffer)
        end
    end

    httpServer.listen(8080)

## SockJS client

For full information on using the SockJS client library please see the SockJS website. A simple example:

    <script>
       var sock = new SockJS('http://mydomain.com/my_prefix');

       sock.onopen = function() {
           console.log('open');
       };

       sock.onmessage = function(e) {
           console.log('message', e.data);
       };

       sock.onclose = function() {
           console.log('close');
       };
    </script>
    
As you can see the API is very similar to the WebSockets API.    

# SockJS - EventBus Bridge

## Setting up the Bridge

By connecting up SockJS and the vert.x event bus we create a distributed event bus which not only spans multiple vert.x instances on the server side, but can also include client side JavaScript running in browsers.

We can therefore create a huge distributed bus encompassing many browsers and servers. The browsers don't have to be connected to the same server as long as the servers are connected.

On the server side we have already discussed the event bus API.

We also provide a client side JavaScript library called `vertxbus.js` which provides the same event bus API, but on the client side.

This library internally uses SockJS to send and receive data to a SockJS vert.x server called the SockJS bridge. It's the bridge's responsibility to bridge data between SockJS sockets and the event bus on the server side.

Creating a Sock JS bridge is simple. You just call the `bridge` method on the SockJS server instance.

You will also need to secure the bridge (see below).

The following example creates and starts a SockJS bridge which will bridge any events sent to the path `eventbus` on to the server side event bus.

    server = Vertx::HttpServer.new;
    
    sockJSServer = Vertx::SockJSServer.new(server)

    sockJSServer.bridge({'prefix' => '/eventbus'}, [], [])

    server.listen(8080)

The SockJS bridge currently only works with JSON event bus messages.

## Using the Event Bus from client side JavaScript

Once you've set up a bridge, you can use the event bus from the client side as follows:

In your web page, you need to load the script `vertxbus.js`, then you can access the vert.x event bus API. Here's a rough idea of how to use it. For a full working examples, please consult the bundled examples.

    <script src="http://cdn.sockjs.org/sockjs-0.2.1.min.js"></script>
    <script src='vertxbus.js'></script>

    <script>

        var eb = new vertx.EventBus('http://localhost:8080/eventbus');
        
        eb.onopen = function() {
        
          eb.registerHandler('some-address', function(message) {

            console.log('received a message: ' + JSON.stringify(message);

          });

          eb.send('some-address', {name: 'tim', age: 587});
        
        }
       
    </script>

You can find `vertxbus.js` in the `client` directory of the vert.x distribution.

The first thing the example does is to create a instance of the event bus

    var eb = new vertx.EventBus('http://localhost:8080/eventbus');

The parameter to the constructor is the URI where to connect to the event bus. Since we create our bridge with the prefix `eventbus` we will connect there.

You can't actually do anything with the bridge until it is opened. When it is open the `onopen` handler will be called.

The client side event bus API for registering and unregistering handlers and for sending messages is exactly the same as the server side one. Please consult the JavaScript core manual chapter on the EventBus for a description of that API.

**There is one more thing to do before getting this working, please read the following section....**

## Securing the Bridge

If you started a bridge like in the above example without securing it, and attempted to send messages through it you'd find that the messages mysteriously disappeared. What happened to them?

For most applications you probably don't want client side JavaScript being able to send just any message to any verticle on the server side or to all other browsers.

For example, you may have a persistor verticle on the event bus which allows data to be accessed or deleted. We don't want badly behaved or malicious clients being able to delete all the data in your database! Also, we don't necessarily want any client to be able to listen in on any topic.

To deal with this, a SockJS bridge will, by default refuse to let through any messages. It's up to you to tell the bridge what messages are ok for it to pass through. (There is an exception for reply messages which are always allowed through).

In other words the bridge acts like a kind of firewall which has a default *deny-all* policy.

Configuring the bridge to tell it what messages it should pass through is easy. You pass in two arrays of JSON objects that represent *matches*, as the final argument in the call to `bridge`.

The first array is the *inbound* list and represents the messages that you want to allow through from the client to the server. The second array is the *outbound* list and represents the messages that you want to allow through from the server to the client.

Each match can have up to three fields:

1. `address`: This represents the exact address the message is being sent to. If you want to filter messages based on an exact address you use this field.
2. `address_re`: This is a regular expression that will be matched against the address. If you want to filter messages based on a regular expression you use this field. If the `address` field is specified this field will be ignored.
3. `match`: This allows you to filter messages based on their structure. Any fields in the match must exist in the message with the same values for them to be passed. This currently only works with JSON messages.

When a message arrives at the bridge, it will look through the available permitted entries.

* If an `address` field has been specified then the `address` must match exactly with the address of the message for it to be considered matched.

* If an `address` field has not been specified and an `address_re` field has been specified then the regular expression in `address_re` must match with the address of the message for it to be considered matched.

* If a `match` field has been specified, then also the structure of the message must match.


Here is an example:

    server = Vertx::HttpServer.new
    
    sockJSServer = Vertx::SockJSServer.new(server)

    sockJSServer.bridge({'prefix' => '/eventbus'},
      [
        # Let through any messages sent to 'demo.orderMgr'
        {
          'address' => 'demo.orderMgr'
        },
        # Allow calls to the address 'demo.persistor' as long as the messages
        # have an action field with value 'find' and a collection field with value
        # 'albums'
        {
          'address' => 'demo.persistor',
          'match' => {
            'action' => 'find',
            'collection' => 'albums'
          }
        },
        # Allow through any message with a field `wibble` with value `foo`.
        {
          'match' => {
            'wibble' => 'foo'
          }
        }
      ],
      [
        # Let through any messages coming from address 'ticker.mystock'
        {
          'address' => 'ticker.mystock'
        },
        # Let through any messages from addresses starting with "news." (e.g. news.europe, news.usa, etc)
        {
          'address_re' => 'news\\..+'
        }
      ])


    server.listen(8080)

To let all messages through you can specify two arrays with a single empty JSON object which will match all messages.

     sockJSServer.bridge({'prefix' => '/eventbus'}, [{}], [{}])

**Be very careful!**

## Messages that require authorisation

The bridge can also refuse to let certain messages through if the user is not authorised.

To enable this you need to make sure an instance of the `vertx.auth-mgr` module is available on the event bus. (Please see the modules manual for a full description of modules).

To tell the bridge that certain messages require authorisation before being passed, you add the field `requires_auth` with the value of `true` in the match. The default value is `false`. For example, the following match:

    {
      'address' => 'demo.persistor',
      'match' => {
        'action' => 'find',
        'collection' => 'albums'
      },
      'requires_auth` => true
    }
    
This tells the bridge that any messages to save orders in the `orders` collection, will only be passed if the user is successful authenticated (i.e. logged in ok) first.    
    
When a message is sent from the client that requires authorisation, the client must pass a field `sessionID` with the message that contains the unique session ID that they obtained when they logged in with the `auth-mgr`.

When the bridge receives such a message, it will send a message to the `auth-mgr` to see if the session is authorised for that message. If the session is authorised the bridge will cache the authorisation for a certain amount of time (five minutes by default)

# File System

Vert.x lets you manipulate files on the file system. File system operations are asynchronous and take a handler block as the last argument.

This block will be called when the operation is complete, or an error has occurred.

The first argument passed into the block is an exception, if an error occurred. This will be `nil` if the operation completed successfully. If the operation returns a result that will be passed in the second argument to the handler.

## Synchronous forms

For convenience, we also provide synchronous forms of most operations. It's highly recommended the asynchronous forms are always used for real applications.

The synchronous form does not take a handler as an argument and returns its results directly. The name of the synchronous function is the same as the name as the asynchronous form with `_sync` appended.

## copy

Copies a file.

This function can be called in two different ways:

* `copy(source, destination)`

Non recursive file copy. `source` is the source file name. `destination` is the destination file name.

Here's an example:

    Vertx::FileSystem.copy('foo.dat', 'bar.dat') do |err, res|
        puts 'Copy was successful' if !err        
    end


* `copy(source, destination, recursive)`

Recursive copy. `source` is the source file name. `destination` is the destination file name. `recursive` is a boolean flag - if `true` and source is a directory, then a recursive copy of the directory and all its contents will be attempted.

## move

Moves a file.

`move(source, destination)`

`source` is the source file name. `destination` is the destination file name.

## truncate

Truncates a file.

`truncate(file, len)`

`file` is the file name of the file to truncate. `len` is the length in bytes to truncate it to.

## chmod

Changes permissions on a file or directory.

This function can be called in two different ways:

* `chmod(file, perms)`.

Change permissions on a file.

`file` is the file name. `perms` is a Unix style permissions string made up of 9 characters. The first three are the owner's permissions. The second three are the group's permissions and the third three are others permissions. In each group of three if the first character is `r` then it represents a read permission. If the second character is `w`  it represents write permission. If the third character is `x` it represents execute permission. If the entity does not have the permission the letter is replaced with `-`. Some examples:

    rwxr-xr-x
    r--r--r--

* `chmod(file, perms, dir_perms)`.

Recursively change permissions on a directory. `file` is the directory name. `perms` is a Unix style permissions to apply recursively to any files in the directory. `dir_perms` is a Unix style permissions string to apply to the directory and any other child directories recursively.

## props

Retrieve properties of a file.

`props(file)`

`file` is the file name. The props are returned in the handler. The results is an object with the following properties/methods:

* `creation_time`: Time of file creation.
* `last_access_time`: Time of last file access.
* `last_modified_time`: Time file was last modified.
* `directory?`: This will have the value `true` if the file is a directory.
* `regular_file?`: This will have the value `true` if the file is a regular file (not symlink or directory).
* `symbolic_link?`: This will have the value `true` if the file is a symbolic link.
* `other?`: This will have the value `true` if the file is another type.

Here's an example:

    Vertx::FileSystem.props('some-file.txt') do |err, props|
        if err
            puts "Failed to retrieve file props: #{err}"
        else
            puts 'File props are:'
            puts "Last accessed: #{props.lastAccessTime}"
            // etc
        end
    end

## lprops

Retrieve properties of a link. This is like `props` but should be used when you want to retrieve properties of a link itself without following it.

It takes the same arguments and provides the same results as `props`.

## link

Create a hard link.

`link(link, existing)`

`link` is the name of the link. `existing` is the existing file (i.e. where to point the link at).

## symlink

Create a symbolic link.

`sym_link(link, existing)`

`link` is the name of the symlink. `existing` is the existing file (i.e. where to point the symlink at).

## unlink

Unlink (delete) a link.

`unlink(link)`

`link` is the name of the link to unlink.

## read_sym_link

Reads a symbolic link. I.e returns the path representing the file that the symbolic link specified by `link` points to.

`read_sym_link(link)`

`link` is the name of the link to read. An usage example would be:

    Vertx::FileSystem.read_sym_link('somelink') do |err, res|
        puts "Link points at #{res}" if !err
    end

## delete

Deletes a file or recursively deletes a directory.

This function can be called in two ways:

* `delete(file)`

Deletes a file. `file` is the file name.

* `delete(file, recursive)`

If `recursive` is `true`, it deletes a directory with name `file`, recursively. Otherwise it just deletes a file.

## mkdir

Creates a directory.

This function can be called in three ways:

* `mkdir(dirname)`

Makes a new empty directory with name `dirname`, and default permissions `

* `mkdir(dirname, create_parents)`

If `create_parents` is `true`, this creates a new directory and creates any of its parents too. Here's an example

    Vertx::FileSystem.mkdir('a/b/c', true) do |err, res|
       puts "Directory created ok" if !err       
    end

* `mkdir(dirname, create_parents, perms)`

Like `mkdir(dirname, create_parents, handler)`, but also allows permissions for the newly created director(ies) to be specified. `perms` is a Unix style permissions string as explained earlier.

## read_dir

Reads a directory. I.e. lists the contents of the directory.

This method can be called in two ways:

* `read_dir(dir_name)`

Lists the contents of a directory

* `read_dir(dir_name, filter)`

List only the contents of a directory which match the filter. Here's an example which only lists files with an extension `txt` in a directory.

    Vertx::FileSystem.read_dir('mydirectory', '.*\.txt') do |err, res|
      if !err
        puts 'Directory contains these .txt files'
        res.each do |filename|
            puts filename
        end
      end
    end
    
The filter is a regular expression.    

## read_file_as_buffer

Read the entire contents of a file in one go. *Be careful if using this with large files since the entire file will be stored in memory at once*.

`read_file_as_bufer(file)`. Where `file` is the file name of the file to read.

The body of the file will be returned as a `Buffer` in the handler.

Here is an example:

    Vertx::FileSystem.read_file_as_buffer('myfile.dat') do |err, res|
        puts "File contains: #{res.length} bytes" if !err        
    end

## write_buffer_to_file

Writes an entire `Buffer` into a new file on disk

`write_buffer_to_file(file, data)` Where `file` is the file name. `data` is a `Buffer` or string.

## create_file

Creates a new empty file.

`create_file(file)`. Where `file` is the file name.

## exists?

Checks if a file exists.

`exists?(file)`. Where `file` is the file name.

The result is returned in the handler.

    Vertx::FileSystem.exists?('some-file.txt') do |err, res|
    puts "File #{res ? 'exists' : 'does not exist'}" if !err        
end

## fs_props

Get properties for the file system.

`fs_props(file)`. Where `file` is any file on the file system.

The result is returned in the handler. The result object has the following fields:

* `total_space`. Total space on the file system in bytes.
* `unallocated_space`. Unallocated space on the file system in bytes.
* `usable_space`. Usable space on the file system in bytes.

Here is an example:

    Vertx::FileSystem.fs_props('mydir') do |err, res|
      puts "total space: #{res.total_space}" if !err
    end

## open

Opens an asynchronous file for reading \ writing.

* `open(path, perms = nil, read = true, write = true, create_new = true, flush = false)`

When the file is opened, an instance of `AsyncFile` is passed into the result handler block:

    Vertx::FileSystem.open('some-file.dat') do |err, async_file|
        if err
            puts "Failed to open file #{err}"
        else
            puts 'File opened ok'
            async_file.close
        end
    end
    
If `read` is `true`, the file will be opened for reading. If `write` is `true` the file will be opened for writing. If `create_new` is `true`, the file will be created if it doesn't already exist. If `flush` is `true` then every write on the file will be automatically flushed (synced) from the OS cache.    
If the file is created, `perms` is a Unix-style permissions string used to describe the file permissions for the newly created file.

## AsyncFile

Instances of `AsyncFile` are returned from calls to `open` and you use them to read from and write to files asynchronously. They allow asynchronous random file access.

AsyncFile can provide instances of `ReadStream` and `WriteStream` via the `getReadStream` and `getWriteStream` functions, so you can pump files to and from other stream objects such as net sockets, http requests and responses, and WebSockets.

They also allow you to read and write directly to them.

### Random access writes

To use an AsyncFile for random access writing you use the `write` method.

`write(buffer, position, &block)`.

The parameters to the method are: 

* `buffer`: the buffer to write.
* `position`: an integer position in the file where to write the buffer. If the position is greater or equal to the size of the file, the file will be enlarged to accomodate the offset.
* `block`: a block to call when the operation is complete.

Here is an example of random access writes:

    Vertx::FileSystem.open('some-file.dat') do |err, async_file|
        if err
            puts "Failed to open file #{err}"
        else
            # File open, write a buffer 5 times into a file
            buff = Vertx::Buffer.create('foo')
            (1..5).each do |i|            
                async_file.write(buff, buff.length() * i) do |err, res|
                    if err
                        puts "Failed to write #{err}"
                    else
                        puts 'Written ok'
                    end
                end
            end
        end
    end     

### Random access reads

To use an AsyncFile for random access reads you use the `read` method.

`read(buffer, offset, position, length, &block)`.

The parameters to the method are: 

* `buffer`: the buffer into which the data will be read.
* `offset`: an integer offset into the buffer where the read data will be placed.
* `position`: the position in the file where to read data from.
* `length`: the number of bytes of data to read
* `block`: a block to call when the operation is complete.

Here's an example of random access reads:

    Vertx::FileSystem.open('some-file.dat') do |err, async_file|
        if err
            puts "Failed to open file #{err}"
        else 
            buff = Vertx::Buffer.create(1000)
            (1..10).each do |i|
                async_file.read(buff, i * 100, i * 100, 100) do |err, res|
                    if err
                        puts "Failed to read #{err}"
                    else
                        puts 'Read ok'
                    end
                end
            end
        end
    end

### Flushing data to underlying storage.

If the AsyncFile was not opened with `flush = true`, then you can manually flush any writes from the OS cache by calling the `flush` method.

### Using AsyncFile as `ReadStream` and `WriteStream`

Use the methods `read_stream` and `write_stream` to get read and write streams. You can then use them with a pump to pump data to and from other read and write streams.

Here's an example of pumping data from a file on a client to a HTTP request:

    client = Vertx::HttpClient.new
    client.host = 'foo.com'

    Vertx::FileSystem.open('some-file.dat') do |err, async_file|
        if err
            puts "Failed to open file #{err}"
        else
            request = client.put('/uploads') do |resp|
                puts "resp status code #{resp.status_code}"
            end
            rs = asyncFile.read_stream
            pump = Vertx::Pump.new(rs, request)
            pump.start
            rs.end_handler do 
                # File sent, end HTTP requuest
                request.end
            end

        end
    end

### Closing an AsyncFile

To close an AsyncFile call the `close` method. Closing is asynchronous and if you want to be notified when the close has been completed you can specify a handler block as an argument to `close`.








