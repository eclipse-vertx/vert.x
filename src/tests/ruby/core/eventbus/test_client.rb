require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new
@tu.check_context

def test_simple_send

  json = {'message' => 'hello world!'}
  address = "some-address"

  id = EventBus.register_handler(address) do |msg|
    @tu.azzert(msg.body['message'] == json['message'])
    EventBus.unregister_handler(id)
    @tu.test_complete
  end

  @tu.azzert(id != nil)

  EventBus.send(address, json)
end

def test_send_empty

  json = {}
  address = "some-address"

  id = EventBus.register_handler(address) do |msg|
    @tu.azzert(msg.body.empty?)
    EventBus.unregister_handler(id)
    @tu.test_complete
  end

  @tu.azzert(id != nil)

  EventBus.send(address, json)
end

def test_reply

  json = {'message' => 'hello world!'}
  address = "some-address"
  reply = {'cheese' => 'stilton!'}

  id = EventBus.register_handler(address) do |msg|
    @tu.azzert(msg.body['message'] == json['message'])
    msg.reply(reply)
  end

  @tu.azzert(id != nil)

  EventBus.send(address, json) do |msg|
    @tu.azzert(msg.body['cheese'] == reply['cheese'])
    EventBus.unregister_handler(id)
    @tu.test_complete
  end

end

def test_empty_reply

  json = {'message' => 'hello world!'}
  address = "some-address"
  reply = {}

  id = EventBus.register_handler(address) do |msg|
    @tu.azzert(msg.body['message'] == json['message'])
    msg.reply(reply)
  end

  @tu.azzert(id != nil)

  EventBus.send(address, json) do |msg|
    @tu.azzert(msg.body.empty?)
    EventBus.unregister_handler(id)
    @tu.test_complete
  end

end

def test_send_unregister_send

  json = {'message' => 'hello world!'}
  address = "some-address"

  received = false

  id = EventBus.register_handler(address) do |msg|
    @tu.azzert(false, "handler already called") if received
    @tu.azzert(msg.body['message'] == json['message'])
    EventBus.unregister_handler(id)
    received = true
    # End test on a timer to give time for other messages to arrive
    Vertx.set_timer(100) { @tu.test_complete }
  end

  @tu.azzert(id != nil)

  (1..2).each do
    EventBus.send(address, json)
  end
end

def test_send_multiple_matching_handlers

  json = {'message' => 'hello world!'}
  address = "some-address"

  num_handlers = 10
  count = 0

  (1..num_handlers).each do
    id = EventBus.register_handler(address) do |msg|
      @tu.azzert(msg.body['message'] == json['message'])
      EventBus.unregister_handler(id)
      count += 1
      @tu.test_complete if count == num_handlers
    end
  end

  EventBus.send(address, json)
end

def vertx_stop
  @tu.check_context
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
