require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

def test_blocking_action

  num_actions = 100
  count = 0

  for i in (1..num_actions) do
    Vertx.run_blocking do
      "foo"
    end.handler do |fut|
      @tu.check_context
      @tu.azzert(fut.succeeded?)
      @tu.azzert("foo" == fut.result)
      count = count + 1
      if count == 2 * num_actions
        @tu.test_complete
      end
    end

    Vertx.run_blocking do
      raise "oops!"
    end.handler do |fut|
      @tu.check_context
      @tu.azzert(!fut.succeeded?)
      count = count + 1
      if count == 2 * num_actions
        @tu.test_complete
      end
    end
  end
end

def vertx_stop
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
