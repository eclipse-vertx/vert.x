In this tutorial we're going to write a real web-application using vert.x.

The application is a shop application called "vToons" which allows you to buy tracks online.

The application will consist of several components talking to each other on the vert.x distributed event bus.

The components of the application are:

* An MVC client-side JavaScript application which creates the shop front.
For this tutorial I have used knockout.js, but you could quite happily use another client side MVC JS framework such as backbone.js or ember.js, or use your own set of client side tools, whatever they are. Vert.x has no dependency on any specific client side JS frameworks.
The client side JS app talks on the vert.x event bus to the other components that make up the application.

* A server side persistor budmod (bus module).

Vert.x ships with an out of the box persistor for MongoDB. We use the persistor for storing catalogue data for the tracks, as well as user data and for persisting orders

* A server side mailer busmod.

Vert.x ships with an out of the box mailer component. We'll use this to send order confirmation emails.

* A web server to serve the static content, and acts as a SockJS bridge to connect up the client side event bus with the server side.

The entire code for the tutorial is included in the distribution in the directory [TODO]

## Create the web server

Let's start by creating the web server. We're going to use this to serve the static html, css, and JavaScript used by the client side JS application.

Here's the initial code for the web server:




