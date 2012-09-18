<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

[TOC]

# Modules

## What is a Module (mod) ?

Vert.x allows you to package up your applications or re-usable functionality into modules which can then be referenced by other applications or modules.

## Modules location

By default, Vert.x will assume that the modules for your project live in a local `mods` directory relative to where you run Vert.x from. Having a project maintain its own set of modules is the recommended way to structure a Vert.x application.

If you prefer to store your modules in some other place, e.g. a module directory shared by different Vert.x applications you can set the environment variable `VERTX_MODS` to point to that.

## Module naming

A module name can be any string. However, we recommend using the following convention:

    prefix.<name>-v<version>
    
Where `prefix` is a reversed domain name for your company or organisation (like a Java package name), `name` is a descriptive name and `version` is a version string.

If the module has been produced by the Vert.x project we use `vertx` as the prefix.

Here are some examples:

    org.myorg.coolproject.calculator-module-v1.1
    com.acme.monitor-v2.0beta5
    vertx.mongo-persistor-v1.0
    
## Module directory structure

Each actual module lives in its own child directory of the module root directory (given by `mods` or `VERTX_MODS`). The name of the directory is the full name of the module. Modules must not share a directory.

### Module descriptor

Inside the module directory you must also provide a file called `mod.json` which contains some JSON which describes the module, for example:

    {
        "main": "mailer.js"
    }
    
#### Main field
    
Runnable modules must contain a field `main` which specifies the main verticle to start the module. Main would be something like `myscript.groovy`, `app.rb`, `foo.js` or `org.acme.MyApp`. (See the chapter on "running vert.x" in the main manual for a description of what a main is.)

Non-runnable modules (see below) do not need a `main` field.

#### Worker modules

If your main verticle is a worker verticle you must also specify the `worker` field with value `true`, otherwise it will be assumed it is not a worker.

#### Preserving working directory

By default when your module is executing it will see its working directory as the module directory when using the Vert.x API. This is useful if you want to package up an application which contains its own static resources into a module.

However in some cases, you don't want this behaviour, you want the module to see its working directory as the working directory of whoever started the module.

An example of this would be the `web-server` module. With the web server, you use it to serve your own static files which exist either outside of a module or in your own module, they do not exist inside the `web-server` module itself.

If you want to preserve current working directory, set the field `preserve-cwd` to `true` in `mod.json`. The default value is `false`.

#### Auto re-deploy

You can configure a module to be auto-redeployed if it detects any files were modified, added or deleted in its module directory.

To enable auto re-deploy for a module you should specify a field `auto-redeploy` with a value of `true` in `mod.json`. The default value for `auto-redeploy` is `false`.

### Module path

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

## Running a module from the command line

Modules can be run directly by using the command `vertx runmod`. Please see the main manual for a full description of this.

E.g.

    vertx runmod org.myorg.mymod-v3.2
    
## Running a module programmatically

You can also run a module programmatically similarly to how you run any verticle programmatically. Please see the core manual for the appropriate language for a full description on how to do this.

E.g. (JavaScript)

    vertx.deployModule('org.myorg.mymod-v3.2');

## Module working directory

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

## Packaging and installing a module manually

Packaging your own module is simple, simply create your vert.x application as normal, then put it all in a directory whose name is the module name, and provide a `mod.json` file as described above.

To install it, simply copy the entire directory to your local `mods` directory, or the directory specified by `VERTX_MODS` if you have decided to set that.

## The module repository

Vert.x operates a module repository at `http://github.com/vert-x/vertx-mods`. The idea is this contains various useful modules that you might want to use in your own Vert.x applications.

You can also submit your own modules to the module repository by sending a pull request to the repository.

### Installing modules manually from the repository

To install a module manually from the repository use `vertx install <module name>`. Try this now from the command line:

    vertx install vertx.mongo-persistor-v1.0
    
You should now have the `vertx.mongo-persistor` module version 1.0 installed in your local `mods` directory (or `VERTX_MODS` if you are using that).

### Lazy installation of modules

If you attempt to run a module, either on the command line, or programmatically from within another verticle or module, then Vert.x will first look in your local `mods` directory (or `VERTX_MODS`) to see if it is already installed.

If it is already there, it will be deployed. If it is not already there, Vert.x will attempt to download it from the module repository, and install it. 

If Vert.x can't find a module with that name, an error will be reported.

Because of this lazy installation, you can avoid manually installing modules completely, if you so wish, they will be installed automatically the first time they are requested by your application.

### Uninstalling modules

To uninstall a module from your local `mods` directory (or `VERTX_MODS`) use the `vertx uninstall <module name>` command. E.g.

    vertx uninstall vertx.mongo-persistor-v1.0
    
### Using a different module repository

You can tell Vert.x to use a different module repository to the default at `http://github.com/vert-x/vertx-mods` by specifying the `-repo` option when running `vertx run`, `vertx runmod` or `vertx install`. E.g.

    vertx install com.acme.my-module-v2.1.beta -repo http://internalrepo.acme.com
    
This is useful if you want to maintain your own private module repository.    

## Including the resources of modules

Sometimes you might find that different modules are using the same or similar sets of resources, e.g. `.jar` files or scripts, or other resources.

Instead of including the same resources in every module that needs them, you can put those resources in a module of their own, and then declare that other modules `includes` them.

The resources of the module will then effectively be put on the module path of the using module.

This is done by specifying an `includes` field in the module descriptor.

Let's take an example. We have a jar `foo.jar` and a script `bar.js` which we want to be used by both `com.acme.module1-v1.0` and `com.acme.module2-v1.0`.

We create a module that contains `bar.js` and `foo.jar` (in the `lib` directory). Let's call that module `com.acme.common-stuff-v1.0`.

Then in the `mod.json` of `com.acme.module1-v1.0` and `com.acme.module2-v1.0` we add an `includes`, e.g.:

    {
        ...
        "includes":"com.acme.common-stuff-v1.0"
    }

A module can include many other modules - `includes` is a comma-separated list of module names.

If modules that are included in turn include other modules, the entire graph of includes is walked (avoiding circular dependencies) to get the unique set of resources to include on the module path.

If a jar is include more than once from different modules, a warning will be issued.    

Modules that *only* contain resources for re-use are called *non-runnable* modules and they don't require a `main` field in `mod.json`. It's also possible to include the resources of runnable modules into another module.

## Configuration

Configuration for a module (if any) should be specified using a JSON configuration file when deploying from the command line using `vertx runmod` or passed in when deploying a module programmatically.

Applying configuration this way allows it to be easily and consistently configured irrespective of the language.

