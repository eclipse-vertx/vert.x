<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

[TOC]

# Embedding Vert.x

It's highly recommended that you run Vert.x applications as modules using the `vertx` command at the command line, however it is possible to embed Vert.x inside pre-existing Java applications. There are two ways this can be done:

## Embedding the Vert.x platform

The Vert.x platform is the container which knows how to run Vert.x modules and verticles, it also contains the Vert.x module system functionality. You use an instance of the interface `org.vertx.java.platform.PlatformManager` to control the platform.

To get an instance of `PlatformManager` you use the `org.vertx.java.platform.PlatformLocator` class:

    PlatformManager pm = PlatformLocator.factory.createPlatformManager();

Once you have an instance of `PlatformManager` you can deploy and undeploy modules and verticles, install modules and various other actions. See the JavaDoc for more information on the available methods.

Here are some examples:

Deploy a module

    pm.deployModule("com.mycompany~my-module~1.0");

Deploy 10 instances of a raw JS verticle

    pm.deployVerticle("app.js", 10);

The methods available on `PlatformManager` roughly map to the actions performed by the `vertx` command at the command line.

When using the platform manager the following system properties can be set:

* `vertx.home` - When installing system modules, vert.x will install them in a directory `sys-mods` in this directory.
* `vertx.mods` - When looking for or installing non system modules Vert.x will look in the directory `mods` in the current working directory. If `vertx.mods` is set this will tell Vert.x to instead look in the provided directory.


## Embedding Vert.x core

*** Please note this feature is intended for power users only. ***

It's also possible to embed the Vert.x core classes directly and bypass the Vert.x platform altogether.

If you are embedding this way you should be aware that

* You will only be able to use Vert.x with Java, not any of the other languages that Vert.x suppports.
* You will not have access to the Vert.x module system so you will be able to easily benefit from functionality provided by the community in the form of modules. Consider that most  functionality in Vert.x will be in the form of modules.
* You will not benefit from the automatic compilation of Java verticles at run-time. You will have to compile your classes manually.
* You will have to manage scaling of your application manually by programmatically creating more instances of your servers. You will not be able to benefit from the `-instances` option of the `vertx` command or in the `PlatformManager` API.
* You will need to manually ensure you do not have concurrent access to non thread-safe core objects in your application. The Vert.x platform would normally protect you against this.

If you are happy with the above limitations then Vert.x can be used directly in a Java application as the following echo server example demonstrates

    public class Embedded {
      public static void main(String[] args) throws Exception {

        Vertx vertx = VertxFactory.newVertx();

        // Create an echo server
        vertx.createNetServer().connectHandler(new Handler<NetSocket>() {
          public void handle(final NetSocket socket) {
            Pump.createPump(socket, socket).start();
          }
        }).listen(1234);

        // Prevent the JVM from exiting
        System.in.read();

      }
    }

You first get a reference to the `Vertx` object using the `VertxFactory` class, then you use the core classes as you wish.

Note that all Vert.x threads are daemon threads and they will *not* prevent the JVM for exiting. Therefore you must ensure that the `main()` method does not run to completion, e.g. in this example we have used `System.in.read()` to block the main thread waiting for IO from the console.

### Core thread safety

Many of the Vert.x core classes are *not* thread-safe. When running Vert.x in the Vert.x platform you don't have to worry about that as Vert.x guarantees that your verticle code is never executed by more than one thread concurrently.

When running core embedded you have to be more careful as there is no container to make such guarantees. Therefore it's up to you the developer that you don't allow concurrent access to non thread-safe classes.

Please consult the JavaDoc to see which classe are thread-safe and which are not.

#### Event loops and scaling Vert.x embedded

When running your Vert.x code in a standard verticle, Vert.x ensures it's all executed by the exact same thread (event loop).

When running Vert.x embedded you will start off on one of your own application threads which won't be a Vert.x event loop. When Vert.x calls the handlers of any of the core objects that you create, they *will* be called on an event loop.

Vert.x determines which event loops to use according to the following rules:

If the calling thread is not an event loop then:

* Instances of `NetServer`, `NetClient`, `HttpServer` and `HttpClient` will be assigned an event loop when they are created. When any of their handlers are subsequently called they will be called using that event loop.
* When a handler is registered on the event bus an event loop will be assigned to it. This event loop will then be used every time the handler is called.
* File system operation handlers are called using an event loop determined by Vert.x

If the calling thread is an event loop, then that current event loop will be used.

Since a particular instance of `NetServer`, `HttpServer` will have all their handlers executed on the same event loop, if you want to scale your servers across multiple cores you need to create multiple instances of them, from a thread that is not an event loop.




