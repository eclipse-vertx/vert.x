require "vertx"
include Vertx
require "test_utils"

EventBus.send("test-handler", "started")

def vertx_stop
  EventBus.send("test-handler", "stopped")
end