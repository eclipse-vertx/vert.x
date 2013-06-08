<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

[TOC]

# What is a Vert.x module ?

Vert.x allows you to package up your applications or other re-usable functionality into modules which can then be deployed or used by other Vert.x code. 

Creating your app as module(s) gives you the following benefits:

* Your classpath is encapsulated so modules are easier to run. You don't need to craft any long command lines.
* Your dependencies are encapsulated in a single artifact (the module zip file)
* Your module can be pushed to any Maven repository or Bintray.
* Your module can be catalogued in the Vert.x [module registry](https://vertxmodulereg-vertxmodulereg.rhcloud.com/) so others can discover and use it
* Vert.x can automatically download and install modules from any repository given just the module identifier.

For these reasons it is highly recommend that you always assemble your applications as modules and you don't use raw Verticles for anything other than trivial prototyping and examples.

# Runnable and non runnable modules

Modules can be either *runnable* or *non runnable*.

A *runnable* module has a *main* and can be deployed at the command line using `vertx runmod` or programmatically using `container.deployModule(...)`.

A *non runnable* module does not have a *main* and cannot be deployed, but is instead used to [include](#includes) resources from one module into another.

# Module Identifier

Each module has a unique identifier. The identifier is a string that is composed of three parts:

* Owner - represents the owner of the module. Often this will be a reverse domain name (like a Maven groupID or Java package), but it doesn't have to be - it's just a string.
* Name - the name of the module - again just a string
* Version - the version of the module - also just a string - Vert.x doesn't enforce any particular version naming conventions.

Three parts are separated by the tilda character '~' and concatenating together to form the module identifier string. Why tilda? We experimented with other characters (e.g. `#`, `:`) but it's hard to find something that works on all operating systems (e.g. `:` is a reserved char in Windows filenames) and language implementations.

Examples:

    io.vertx~mod-mongo-persistor~2.0.0-beta1
    com.mycompany~toaster-mod~1.2.1-final
    purplefox~foomod~1.0

# The structure of a module

A Vert.x module is a `zip` file which contains the resources (`.class` files, `.java` files, script files, `jar` files and other resources) needed by your module.

In some ways it is similar to a `jar` file used in the Java world. We deliberately did not use `jar` files for Vert.x modules because Vert.x is a polyglot system and a particular module might, for instance, only contain python scripts, so using a Java archive for this would be somewhat strange!

## Module descriptor file - mod.json

Every module must contain a file `mod.json` in the root of the module. This is the module descriptor file and contains various fields. It's a text file which contains some JSON. We relax the JSON specification a little bit and also allow you to add C-style comments in your `mod.json` so you can annotate your configuration. 

Let's go through the fields in turn:

### main

If the module is runnable then a `main` must be specified. The main represents the verticle that will be run to start the module. The main can be the FQCN of a compiled Java or Groovy verticle or it can be the name of a JavaScript, Groovy, Ruby or Python script verticle. The script/class must be in the module.

Here are some example:

    "main": "org.mycompany.mymod.MyMain"

or

    "main": "app.js"

or

    "main": "somedir/starter.rb"

In order to determine which language implementation module to use to run the main, Vert.x usually looks at the file extension, and uses the mapping defined in the file `langs.properties` in the Vert.x installation `conf` directory to look up the language implementation. In some cases there is ambiguity, for example given a FQCN should we assume the class is Java or Groovy? In these cases the main can be prefixed with the name of the language implementation to use, for example:

    "main": "groovy:org.mycompany.mymod.MyCompiledGroovyClass"

### worker

If the module is a worker this should be set to `true`. Worker modules run using a thread from the background pool and are allowed to perform blocking operations. Standard verticles run using an event loop thread and cannot block. Default is `false`.

    "worker": true

### multi-threaded

If the module is a worker then setting this to `true` makes the worker multi-threaded, i.e. the code in the module can be executed concurrently by different worker threads. This can be useful when writing modules that wrap things like JDBC connection pools. Use this with caution - this a power user feature not intended to be used in normal Vert.x development.

<a id="includes"> </a>
### includes

A module can include zero or more other modules. Including a module means the included modules classloaders are added as parents of the including module class loader. This means classes and resources from other modules can be accessed as if they were available in the including module. In some ways it's similar to importing packages in Java.

The `includes` is a comma separated string of modules to include.

    "includes": "io.vertx~some-module~1.1,org.aardvarks~foo-mod~3.21-beta1"

### preserve-cwd

When a module is executing if it uses Vert.x to access the filesystem it will, by default, see the module directory as the current directory. Let's say you have a web application that you wish to package as a module. This allows you to package up the resources of your web application inside the module and your module will see them.

For example, let's say we have a module with the following structure:

    /mod.json
    /server.js
    /web/index.html

And `server.js` is a web server

    var vertx = require('vertx')
    vertx.createHttpServer().requestHandler(function(req) {
       req.response.sendFile('./web/index.html'); // Always serve the index page
    }).listen(8080, 'foo.com')

By default the server sees the module directory as '.' so it can access the file `web/index.html` which is included in the module.

In some cases you *don't want* the module to see its module directory as its cwd, in which case you can set `preserve-cwd` to `true`. The default is `false`.

    "preserve-cwd": true

This preserves the current working directory of the caller.

### auto-redeploy

Modules can be configured to auto-redeploy if Vert.x detects that any of the module resources have changed. This can be really useful during deployment. For more information see the documentation on [auto redeploy](dev_guide.html#auto-redeploy).

To enable auto-redeploy set `auto-redeploy` to `true`. Default is `false`.

    "auto-redeploy": true

### resident

Usually Vert.x will load a module into memory the first time it is referenced and unload it from memory once it is no longer referenced by any modules. This means that if modules are deployed and undeployed rapidly the referenced modules can end up being loaded and unloaded many times.

For most modules this is not an issue, but for some modules, in particular language implementation modules, the libraries (e.g. language engines) used in those modules may generate a lot of classes which live in the Java permanent generation (permgen), and loading and unloading the module frequently can result in the permgen filling up resulting in out of memory exceptions.

To avoid this, we allow modules to be marked as `resident` which means that Vert.x will load the module the first time it is referenced but will not unload it from memory until the Vert.x instance terminates.

This is an advanced feature and is not intended to be used in normal module development. Default is `false.

    "resident": true

### system

When installing modules, unless the `VERTX_MODS` environment variable is set, Vert.x will install modules in a directory `mods` local to where `vertx` is invoked from. Some modules, in particular, language implementation modules tend to be shared by many different projects and it doesn't make sense to download them separately for each project that uses them.

By marking a module as `system` the module will be installed in the `sys-mods` directory which is in the Vert.x installation directory (if there is one), and will be shared by all applications.

This is an advanced feature and is not intended to be used in normal module development. Default is `false`.

    "system": true

### deploys

Contains a comma separated list of the modules that this module deploys at runtime. It's optional and can be used when [creating self contained modules]().

### description

Text description of the module. This field is mandatory if you want to register the module in the [Vert.x module registry]()

    "description": "This module implements a highly scalable toaster. The toaster....."

### licenses

JSON array of strings containing the names of the license(s) used in the the module. This field is mandatory if you want to register the module in the [Vert.x module registry]()

    "licenses": ["The Apache Software License Version 2.0", "Some other license"]

### author

The primary author of the module. This field is mandatory if you want to register the module in the [Vert.x module registry]()

    "author": "Joe Bloggs"

### keywords

JSON array of strings representing keywords that describe the module. This field is highly recommended if you want to register the module in the [Vert.x module registry](), as it is used when searching for modules.

    "keywords": ["bread", "toasting", "toasters", "nuclear"]

### developers

JSON array of strings representing any other developers of the module. This field is optional.

    "developers": ["A.N. Other", "Genghis Khan"]

### homepage

The homepage of your module project. This field is optional, but highly recommended.

    "homepage": "https://github.com/jbloggs/toaster-mod"

Or

    "homepage": "http://toastermod.org"


## Module lib directory

If your module directly uses other `jar` or `zip` files these should be placed in a `lib` directory which is in the root of the module. Any `jar` or `zip` files in the lib directory will be added to the module classpath.

If you have a particular `jar` which is used by several different modules, instead of adding it to the `lib` directory of each of the modules that uses it you can place it in its own module and *include* that module from the using module. This allows your modules to be smaller in size since they don't all contain the `jar`, and it also means the `jar` classes are only loaded into memory once, and not once for each module type that uses it.

## Examples

Here's the structure of a simple module that just contains a single JS file:

    /mod.json
    /app.js

Where mod.json contains:

    {
      "main": "app.js"
    }

Here's a simple module that contains java classes

    /mod.json
    /com/mycompany/mymod/App.class
    /com/mycompany/mymod/SomeOtherClass.class

Where mod.json contains:

    {
      "main": "com.mycompany.mymod.App"
    }

Another java module which also references some jars:

    /mod.json
    /com/mycompany/myothermod/App.class
    /com/mycompany/myothermod/SomeOtherClass.class
    /lib/somelib.jar
    /lib/someotherlib.jar

And you can put other resources in the module too, e.g.

    /mod.json
    /com/mycompany/myserver/Server.class
    /foo.properties
    /web/index.html

# Module classpath

Each module type is given its own class loader by Vert.x. The root of the module and any `jar` or `zip` files in the `lib` directory (if any) comprise the module classpath. This means your code running in a module can reference any classes or other resources on this classpath.

# Module classloaders

Each module *type* is given its own class loader. For example, all deployed module instances of the module `com.mycompany~foo-mod~1.0` will share the same class loader, and all deployed module instances of the module `com.mycompany~bar-mod~2.1` will share another (different) class loader.

This means that code from different modules is isolated from each other. E.g. you cannot use globals or static members to share data between `com.mycompany~foo-mod~1.0` and `com.mycompany~bar-mod~2.1`.

This means you can run multiple versions of the same module or have multiple versions of the same jar running in different modules in the same Vert.x instance at the same time.

Raw verticles that are run from the command line also run in their own class loader.

Any verticles *types* deployed from a module will also have a their own clasloader based on the main that is being deployed. For example all instances of `foo.js` deployed as verticles from inside `com.mycompany~foo-mod~1.0` will share a class loader, and all instances of (different) `foo.js` deployed as verticles from inside `com.mycompany~bar-mod~2.1` will share a different class loader.

Module class loaders are also arranged in a hierarchy. A particular module classloader can have zero or more parent class loaders. When loading classes or other resources a module class loader will first look on its *own* classpath for those resources, if it cannot find them it will ask each of its parents in turn, and they in turn will ask their parents. Note that this differs from the standard Java class loading protocol which usually asks its parents *first* before looking for resources itself.

If classes or resources cannot be found by any of the module class loaders in the hierarchy the platform class loader (i.e. the class loader that Vert.x itself uses) will be asked.

A raw verticle that is run directly on the command line will not have any parent module class loaders. A verticle that is deployed from inside a module will have the module class loader set as a parent of the class loader used to deploy the verticle.

If a module [includes](#includes) any other modules than each of the modules that it includes will be set as a parent class loader of the module class loader. Doing this allows us to load the classes for any included module only once.

# Non runnable modules - Including the resources of modules

Sometimes you might find that different modules are using the same or similar sets of resources, e.g. `.jar` files or scripts, or other resources.

Instead of including the same resources in every module that needs them, you can put those resources in a module of their own, and then declare that other modules `includes` them.

Doing this adds the class loader of the included module as a parent to the class loader of the including module, in effect meaning you can reference the resources in the included module s if they were present in the including module.

This is done by specifying an `includes` field in the module descriptor.

Let's take an example. We have a jar `foo.jar` and a script `bar.js` which we want to be used by both `com.acme.module1-v1.0` and `com.acme.module2-v1.0`.

We create a module that contains `bar.js` and `foo.jar` (in the `lib` directory). Let's call that module `com.acme.common-stuff-v1.0`.

Then in the `mod.json` of `com.acme.module1-v1.0` and `com.acme.module2-v1.0` we add an `includes`, e.g.:

    {
        ...
        "includes":"com.acme.common-stuff-v1.0"
    }

A module can include many other modules - `includes` is a comma-separated list of module names.

If a jar is include more than once from different modules, a warning will be issued.    

Modules that *only* contain resources for re-use are called *non-runnable* modules and they don't require a `main` field in `mod.json`. It's also possible to include the resources of runnable modules into another module.

# Running a module from the command line

Modules can be run directly by using the command `vertx runmod`. Please see the [main manual]() for a full description of this.

E.g.

    vertx runmod org.myorg~mymod~3.2

You can also run a module given just the module `zip` file. E.g.

    vertx runzip my-mod-3.2.zip

The name of the module zip file must be &lt;module_name&gt;-&lt;version&gt;.zip

    
# Running a module programmatically

You can also run a module programmatically similarly to how you run any verticle programmatically. Please see the core manual for the appropriate language for a full description on how to do this.

E.g. (JavaScript)

    vertx.deployModule('org.myorg~mymod~3.2');

# How Vert.x locates modules

When you attempt to deploy or [include](#includes) a module, Vert.x will first look to see if the module is already installed.

Vert.x looks in the following places:

1. If the module is being deployed from another module it will look for a nested `mods` directory inside the current module. More on this [later]().
2. In the local `mods` directory on the filesystem, or in the directory specified by the `VERTX_MODS` environment variable, if set.
3. In the `sys-mods` directory of the Vert.x installation. 

If the module is not there then Vert.x will look in various repositories to locate the module. In the out of the box Vert.x configuration, Vert.x will look in Maven Central, Sonatype Nexus Snapshots, Bintray and your local Maven repository (if any).

The repositories it looks in are configured in the file `repos.txt` in the Vert.x installation `conf` directory. Here is the default `repos.txt`:

    # Local Maven repo on filesystem
    mavenLocal:~/.m2/repository

    # Maven central
    maven:http://repo2.maven.org/maven2

    # Sonatype snapshots
    maven:http://oss.sonatype.org/content/repositories/snapshots

    # Bintray
    bintray:http://dl.bintray.com

Vert.x can understand any Maven or Bintray repository, so if you want to configure your own repository simply add it to the file. For example, you might have your own company internal Nexus repository that you want to use.

Vert.x queries the repositories in the order they appear in the file, and will stop searching as soon as it finds the module in one of the repositories.

## Mapping a Module Identifier to Maven co-ordinates

When looking for a module in a Maven repository, Vert.x maps the module identifier to Maven co-ordinates as follows:

    GroupID = Owner
    ArtifactID = Name
    Version = Version

E.g. for the module identifier `com.mycompany~my-mod~1.2-beta` the Maven co-ordinates would be 

    GroupID: com.mycompany
    ArtifactID: my-mod
    Version: 1.2-beta

The artifact type is always assumed to be a `zip`.

## Mapping a Module Identifier to Bintray co-ordinates

When looking in a Bintray repository, Vert.x maps the module identifier to Bintray co-ordinates as follows:

    Bintray user name = Owner
    Repository = vertx-mods
    File path = Name
    File name = Name-Version.zip

For example, the module `purplefox~foo-mod~2.2` in Bintray would map to:

    Bintray username: purplefox
    Repository: vertx-mods
    File path = foo-mod
    File name = foo-mod-2.2.zip

This gives a download url for the module:

    http://dl.bintray.com/purplefox/vertx-mods/foo-mod/foo-mod-2.2.zip

# Nested modules - Packaging all module dependencies in the module

The usual behaviour of Vert.x is to download and install module dependencies the first time they are referenced at runtime, if they're not already installed. For some production deployments this is not necessarily desirable - there may be a requirement that modules deployed on a production server must contain everything necessary to run then without calling out to download more stuff at runtime.

For these situations Vert.x supports packaging a module, along with all the other modules that it references inside the same module to give a single self contained deployment unit.

For example, let's say you've created the module `com.mycompany~mod-toaster~1.0` and that module uses the following other modules `io.vertx~mod-mongo-persistor~2.0.0-beta1` and `org.foo~mod-nuclear~2.2`. Normally those modules would be downloaded and installed in the local `mods` directory on the file system the first time the module is run.

By adding the modules to the `deploys` field in the `mod.json` of our module we are telling Vert.x what the runtime module dependencies of the module are (these are not necessarily the same as the *build time* dependencies of the module!).

You can then run the following command against the installed module to tell Vert.x to pull in the dependencies mentioned in the `deploys` field and add them in a *nested* `mods` directory *inside* the module:

    vertx pulldeps <module_name>

Before calling `vertx pulldeps` your module might have the structure on disk:

./mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/mod.json 
./mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/app.js

After calling `vertx pulldeps` it would have the following structure

./mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/mod.json 
./mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/app.js
./mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/mod.json
./mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/[...rest of the stuff in that module]
./mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/mods/org.foo~mod-nuclear~2.2/mod.json
./mods/io.vertx~mod-mongo-persistor~2.0.0-beta1/mods/org.foo~mod-nuclear~2.2/[...rest of the stuff in that module]

The module directory `./mods/io.vertx~mod-mongo-persistor~2.0.0-beta1` can then be zipped and deployed a self contained module and it won't have to download any dependencies at runtime since they will all be found *inside * the module already.

If you're using the standard Gradle template project or used the Vert.x Maven Archetype to create your project then this all happens automatically for you if you enable it in the `pom.xml` or `gradle.properties` file.

## Installing modules manually from the repository

In some cases, you do not want to wait until a module is first referenced by the application before it is installed, you want to install it *ahead of time*. This can be done using the command `vertx install`

To install a module manually from a repository use `vertx install &lt;module name&gt;`. For example:

    vertx install io.vertx~mod-mongo-persistor~2.0.0-beta1
    
# Uninstalling modules

To uninstall a module from your local `mods` directory (or `VERTX_MODS`) use the `vertx uninstall &lt;module name&gt;` command. E.g.

    vertx uninstall io.vertx~mod-mongo-persistor~2.0.0-beta1

# How to publish modules to remote repositories

If you want to make your modules public (and this is highly recommended) you can publish them to any Maven or Bintray repository. Vert.x has been designed to work well with the repositories already used by most JVM developers (in most cases this is Maven) and we do now want to impose yet another repository format on users.

## Publishing to Maven repositories

Many JVM developers will already be familiar with working with Nexus repositories and Maven Central and Vert.x modules can be effortlessly published there when using the [Gradle template project](gradle_dev.html) or a project created by the Vert.x [Maven Archetype](maven_dev.html).

For Maven projects this is accomplished by executing `mvn deploy` as is normal for any Maven project.

For Gradle projects you just run `./gradlew uploadArchives` as normal for any Gradle project.

If you're not using Maven or Gradle you can can manually upload your module using the Nexus user interface.

If you're going to use Sonatype Nexus you will need to obtain an account with them.

## Publishing to Bintray

[Bintray](http://bintray.com) is a new binary repository site which arguably is easier to get started with if you're not already used to dealing with publishing artifacts to Maven repositories. You simply need to register for an account on their website then you can immediately start uploading binaries either through their web UI or using command line tools.

# Telling the world about your module - Introducing the Vert.x Module Registry

So you've pushed your module to Maven Central, Bintray or perhaps some other public Maven repository. That's sufficient for any Vert.x user to use it, but how are you going to tell the Vert.x community about it?

Enter the [module registry](https://vertxmodulereg-vertxmodulereg.rhcloud.com/). The Vert.x module registry is a web application that keeps a directory of publicly available Vert.x modules. It allows you to list and search for modules that have been published by other Vert.x users and that might be of interest to you in your applications.

We want to encourage an ecosystem of modules for Vert.x, and the module registry is a key part of that.

The module registry doesn't actually store the modules itself, you store your module in any Maven or Binatray repository, it simply stores meta-data for the module so people can find it.

Once a user has found a module they like, you simply take note of the module identifier and use that in your application. Based on the module identifier Vert.x will download and install the module lazily the first time it is used by your program.

Anyone can register a module with the module registry by filling out a very simple form. Once a regsitration request has been submitted and email will be sent to a moderator who will approve the submission if all is ok.

Any modules that are to be registered in the module registry must have the following fields in `mod.json`:

* `description`
* `licenses`
* `author`

It's also *highly recommended* that the following fields are added too:

* `keywords` - useful when searching
* `homepage` - url to the project homepage.

> Piece of Trivia: The Vert.x module registry is itself a Vert.x module, and you can find it.. it in the Vert.x module registry! 


# Module configuration

Configuration for a module (if any) should be specified using a JSON configuration file when deploying from the command line using `vertx runmod` or passed in when deploying a module programmatically.

Applying configuration this way allows it to be easily and consistently configured irrespective of the language.

