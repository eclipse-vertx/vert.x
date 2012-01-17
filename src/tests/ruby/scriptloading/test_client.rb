require "vertx"
include Vertx
require "test_utils"
require 'scriptloading/script1'

@tu = TestUtils.new

def test_scriptloading
  @tu.azzert(Foo::func1(@tu) == "foo")
  @tu.test_complete
end

def vertx_stop
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
