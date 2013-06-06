<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

[TOC]

# Developing with Vert.x

## Create your application using modules

Although Vert.x allows you to run simple Verticles directly from the command line this is only really intended for quick prototyping or trivial applications. 

For any real application it's highly recommended that you create it as a (set of) Vert.x modules.

Creating your app as module(s) gives you the following benefits:

* Your classpath is encapsulated so modules are easier to run. You don't need to craft any long command lines.
* Your dependencies are encapsulated in a single artifact (the module zip file)
* Your module can be pushed to any Maven repository or Bintray.
* Your module can be catalogued in the Vert.x [module registry](https://vertxmodulereg-vertxmodulereg.rhcloud.com/) so others can discover and use it
* Vert.x can automatically download and install modules from any repository given just the module identifier.

If your application is relatively small it might make sense to create it as a single module. If your application is large split it up into a set of modules.

## Using a Verticle to co-ordinate loading of an application

If you have an application that is composed of multiple verticles that all need to be started at application start-up, then you can use another verticle that maintains the application configuration and starts all the other verticles. You can think of this as your application starter verticle.

For example, you could create a verticle `app.js` (you could use another scripting language - Ruby, Groovy or Python if you prefer) as follows:
    
    // Start the verticles that make up the app  
    
    vertx.deployVerticle("verticle1.js", appConfig.verticle1Config);
    vertx.deployVerticle("verticle2.js", appConfig.verticle2Config, 5);
    vertx.deployVerticle("verticle3.js", appConfig.verticle3Config);
    vertx.deployWorkerVerticle("verticle4.js", appConfig.verticle4Config);
    vertx.deployWorkerVerticle("verticle5.js", appConfig.verticle5Config, 10);

Then set the `app.js` verticle as the main of your module and then you can start your entire application by simply running:

    vertx runmod com.mycompany~my-mod~1.0 -conf conf.json

Where conf.json is a config file like:

    // Application config
    {
        verticle1Config: {
            // Config for verticle1
        },
        verticle2Config: {
            // Config for verticle2
        }, 
        verticle3Config: {
            // Config for verticle3
        },
        verticle4Config: {
            // Config for verticle4
        },
        verticle5Config: {
            // Config for verticle5
        }  
    }  

If your application is large and actually composed of multiple modules rather than verticles you can use the same technique.

## Use the Gradle template project or Maven archetype

If you want to get started quickly it's recommended you use the [Gradle template project](gradle_dev.html) or the [Maven archetype](maven_dev.html) (depending whether you prefer Gradle or Maven) to get your project up and running.

Vert.x is agnostic about what build tool you use, but doing it this way means there is much less project setup to do before you can get going developing, building and testing your module(s).

## Create one project per module of your application

Have each project output one and only one artifact (the module zip file). You can then use

    mvn install

Or

    ./gradlew install

To push a module to your local Maven repository when you make a changem, and it will be automatically picked up by your other modules. Vert.x module system understands how to pull modules from local (as well as remote) Maven repositories.

<a id="auto-redeploy"> </a>
## Auto redeploy and see your changes immediately

When developing a Vert.x module, especially if it has a web interface, it's useful to have your module running and have it automatically pick up any changes in classes or other resources in the module without you having to rebuild.

Vert.x now supports this! (Vert.x 2.0 latest master).

To get this just open your Vert.x project in your IDE as normal. Then, in another window run your module using  a command line like:

    vertx runmod com.yourcompany~your-module~1.0 -cp <classpath>

Where `<classpath>` is a classpath where it will look for the resources of your project.

In the case of a standard Maven project running in IntelliJ IDEA you want to add the `src/main/resources` and `src/test/resources` directories and the `out/production/<intellij_mod_name>` and the `out/test/<intellij_mod_name>` to the classpath.

An example would be:

    vertx runmod com.yourcompany~your-module~1.0 -cp src/main/resources/:src/test/resources/:out/production/mod-web-server/:out/test/mod-web-server/

This should also work with Eclipse by subsituting the out directory for wherever Eclipse copies compiled files to (`bin` directory?)

Then just edit your files in your IDEA and when they're saved/compiled the module should be automatically restarted with your changes.



    
 
