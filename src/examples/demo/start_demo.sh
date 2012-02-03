#!/bin/sh

# Copy vertxbus.js into the web app - only need to do this when running from src tree
cp ../../client/vertxbus.js web/js

../vertx-dev deploy -worker mailer.js
../vertx-dev deploy -worker persistor.js
../vertx-dev deploy static_data.js
../vertx-dev deploy order_queue.js
../vertx-dev deploy -worker order_processor.js -instances 10
../vertx-dev deploy auth_mgr.js
../vertx-dev deploy order_mgr.js
../vertx-dev deploy web_server.js