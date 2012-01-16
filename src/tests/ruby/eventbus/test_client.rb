require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new
@tu.check_context

def test_event_bus

  body = "hello world!"
  address = "some-address"

  id = EventBus.register_handler(address) do |msg|
    @tu.azzert body == msg.body.to_s
    msg.reply
  end

  buff = Buffer.create_from_str(body)
  msg = Message.new(address, buff)

  EventBus.send(msg) do
    # receipt handler
    EventBus.unregister_handler(id)
    @tu.test_complete
  end

end

def vertx_stop
  @tu.check_context
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
