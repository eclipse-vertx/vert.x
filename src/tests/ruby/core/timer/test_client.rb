require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new
@tu.check_context

def test_one_off
  Vertx::set_timer(10) { |timer_id|
    @tu.check_context
    @tu.test_complete
  }
end

def test_periodic

  fires = 10

  count = 0
  Vertx::set_periodic(10) { |timer_id|
    @tu.check_context
    count += 1
    if count == fires
      Vertx::cancel_timer(timer_id)
      # End test in another timer in case the first timer fires again - we want to catch that
      Vertx::set_timer(100) { |timer_id|
        @tu.test_complete
      }
    elsif count > fires
      @tu.azzert(false, 'Fired too many times')
    end
  }

end

def vertx_stop
  @tu.check_context
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
