require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

def test_isolation
  # Make sure global variables aren't visible between applications
  @tu.azzert($test_global == nil)
  $test_global = 123
  @tu.test_complete
end

def vertx_stop
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
