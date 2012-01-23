#!/bin/sh

# Copy vertxbus.js into the web app - only need to do this when running from src tree
cp ../../client/vertxbus.js web/js

../vertx-dev deploy -js -worker -main mailer.js -cp . -instances 1
../vertx-dev deploy -js -worker -main persistor.js -cp . -instances 1
../vertx-dev deploy -js -main order_manager.js -cp . -instances 1
../vertx-dev deploy -js -main order_queue.js -cp . -instances 1
../vertx-dev deploy -js -worker -main order_processor.js -cp . -instances 10
../vertx-dev deploy -ruby -main web_server.rb -cp .