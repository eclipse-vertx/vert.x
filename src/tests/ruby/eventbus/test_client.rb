require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new
@tu.check_context

def test_event_bus

  json = {'message' => 'hello world!'}
  address = "some-address"
  reply = {'cheese' => 'stilton!'}

  id = EventBus.register_handler(address) do |msg|
    @tu.azzert(msg.json_object['message'] == json['message'])
    msg.reply(reply)
  end

  EventBus.send(address, json) do |msg|
    @tu.azzert(msg.json_object['cheese'] == reply['cheese'])
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
