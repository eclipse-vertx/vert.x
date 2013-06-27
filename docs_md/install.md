<!--
This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/ or send
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
-->

Before you can do anything with vert.x you need to install it, so let's describe how to do that.

# Getting a distro

The easiest way to get hold of a distribution is to [download a binary distro](downloads.html).
Alternatively you can build from source. To do that see the instructions on the [github wiki](https://github.com/vert-x/vert.x/wiki).

# Pre-requisites

* Operating System. Vert.x runs out of the box on Linux, OSX or Windows.

* JDK. Vert.x requires JDK 1.7.0 or later. You can use the official Oracle distribution or the OpenJDK version. Make sure the JDK bin directory is on your `PATH`.

* JRuby. If you want to use Ruby with Vert.x you will need to have installed JRuby and have the `JRUBY_HOME` environment variable pointing at the base of your JRuby installation. You will also need to install the `json` Ruby Gem since this is used in Vert.x. You can do this by running `jruby -S gem install json`. If you're not using Ruby in Vert.x you don't need any of this.

* Jython. If you want to use Python with Vert.x you will need to have installed Jython and have the `JYTHON_HOME` environment variable pointing at the base of your Jython installation. If you're not using Jython in Vert.x you don't need any of this. 


# Install vert.x

Once you've got the pre-requisites installed, you install vert.x as follows:

1. Unzip the distro somewhere sensible (e.g. your home directory)
2. Add the vert.x `bin` directory to your `PATH`.

## Check the version

To make sure you've installed it correctly, open another console and type:

    tim@Ethel:~/example$ vertx version
    vert.x 1.3.1.final

You should see output something like the above.

# Testing the install

Let's test the install by writing a simple web server.

Copy the following into a text editor and save it as `server.js`

    load('vertx.js');

    vertx.createHttpServer().requestHandler(function(req) {
      req.response.end("Hello World!");
    }).listen(8080, 'localhost');

Open a console in the directory where you saved it, and type:

    vertx run server.js

Open your browser and point it at `http://localhost:8080`

If you see "Hello World!" in the browser then you're all set to go!
