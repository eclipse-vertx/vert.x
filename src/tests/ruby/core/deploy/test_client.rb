require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

@handler_id = nil
@verticle_id = nil;

def test_deploy

  @handler_id = EventBus.register_handler("test-handler") do |message|
    @tu.test_complete if "started" == message.body
  end

  @verticle_id = Vertx.deploy_verticle("core/deploy/child.rb")
end

def test_undeploy
  id = Vertx.deploy_verticle("core/deploy/child.rb")

  Vertx.set_timer(100) do
    @handler_id = EventBus.register_handler("test-handler") do |message|
      @tu.test_complete if "stopped" == message.body
    end
    Vertx.undeploy_verticle(id)
  end
end

def vertx_stop
  EventBus.unregister_handler(@handler_id)
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
