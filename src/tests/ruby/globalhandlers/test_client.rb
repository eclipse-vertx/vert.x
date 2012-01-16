require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

@tu.check_context

def test_global_handlers

  key1 = "actor1"
  key2 = "actor2"

  shared_hash = SharedData::get_hash("foo")

  msg1 = "hello from outer"
  msg2 = "hello from actor1"

  id1 = Vertx::register_handler { |msg|
    @tu.azzert(msg1 == msg)
    id2 = shared_hash[key2]
    Vertx::send_to_handler(id2, msg2)
  }
  shared_hash[key1] = id1

  id2 = Vertx::register_handler { |msg|
    @tu.azzert(msg2 == msg)
    Vertx::unregister_handler(id2)
    @tu.test_complete
  }
  shared_hash[key2] = id2

  id1 = shared_hash[key1]
  Vertx::send_to_handler(id1, msg1)

end

def vertx_stop
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
