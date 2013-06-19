<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

[TOC]

# Working with the Example Project 

The example project builds a single Vert.x module. If you have multiple Vert.x modules in your application you should create a different project for each one.

It's highly recommended you read the Vert.x main manual and the Vert.x module manual so you understand the basics of Vert.x and modules before starting out.

The example project has a standard "Maven-style" directory layout which will probably already be familiar to you.

## The module

All Vert.x modules contain a `mod.json` descriptor. This a file containing some json which describes the module. Amongst other things it usually contains a `main` field.
This tells Vert.x what to run when the module is deployed.

The `mod.json` file is in the `src/main/resources` directory of the project. Any files in this directory are copied to the root of the module during packaging.

The thing that is run is called a `Verticle`. A `Verticle` can be written in any of the languages that Vert.x supports. In this case we have a simple Java Verticle called `PingVerticle`. You can see the source for that in the `src/main/java` sub tree of the project.

The verticle has a `start()` method which is called when the verticle is deployed. In the `start()` method the verticle simply registers a handler on the event bus against address `ping-address`. The handler will be called when a message is received at that address. When a message is received the verticle simply replies to the `ping!` with a `pong!`. 

If you'd rather write your main verticle in another language, or mix and match a few verticles with different languages in the module, that's fine. Just create the verticles for your scripting languages in the `src\main\resources` directory and they'll be copied into your module. 


## The tests

The rest of the stuff in the example project is a set of example tests.

### Unit tests

Unit tests are tests that run outside the Vert.x container and which work with your module's Java classes directly. They go in `src\test\unit`.

You can run unit tests in your IDE as normal by right clicking on the test or on the command line.

### Integration tests

We define integration tests here to mean Vert.x tests that are run in the Vert.x container. We provide a custom JUnit test runner which auto-magically takes your tests, starts up a Vert.x container, runs your test in it, and then reports the test results back, all as if the tests had been run locally as normal Junit tests.

You can even write your tests in JavaScript, Groovy, Ruby or Python and still use the familiar JUnit Assert API.

There are example integration tests for each language in the example project. If you are not interested in writing tests in different languages you can safely delete the files you're not interested in.

#### Java integration tests

The example Java integration tests are in `src\test\java\com\mycompany\integration\java`

Java integration tests subclass `TestVerticle` - after all, it's just run inside the Vert.x container as a verticle. Then you just add standard JUnit test methods to it as normal, annotated with `@Test`.

Vert.x integration tests are asynchronous so they won't necessarily have finished until after the test method completes, therefore to signal that the test has completed you should call the method `VertxAssert.testComplete()` at the end.

If your test deploys other verticles you can also assert from there and call `testComplete` from there too.

#### JavaScript integration tests

The example Java integration tests are in `src\test\resources\integration_tests\javascript`

The class in `src\test\java\com\mycompany\integration\javascript` is just a stub class that tells JUnit where the real JavaScript tests are. You can safely ignore it.

#### Ruby integration tests

The example Java integration tests are in `src\test\resources\integration_tests\ruby`

The class in `src\test\java\com\mycompany\integration\ruby` is just a stub class that tells JUnit where the real Ruby tests are. You can safely ignore it.

#### Groovy integration tests

The example Java integration tests are in `src\test\resources\integration_tests\groovy`

The class in `src\test\java\com\mycompany\integration\groovy` is just a stub class that tells JUnit where the real Groovy tests are. You can safely ignore it.

#### Python integration tests

The example Java integration tests are in `src\test\resources\integration_tests\python`

The class in `src\test\java\com\mycompany\integration\python` is just a stub class that tells JUnit where the real Python tests are. You can safely ignore it.

#### Run tests in your IDE

Just open the folder `src\test\java\com\mycompany\integration` in your IDE and right click it and chose to run all tests as JUnit tests (how this is done depends on your IDE). Or you can select individual test classes.

You can also run the tests at the command line if you prefer.

Now you can change your code and re-run the tests and Vert.x will pick up the changes without you having to rebuild at the command line!

You can set breakpoints in your Java code as normal, and debug into your verticle code in the usual way.

