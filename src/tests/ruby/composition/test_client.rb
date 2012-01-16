require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

def test_sync_blocks
  comp = Composer.new

  count = 0

  comp.series{
    count = count + 1
  }

  comp.series{
    count = count + 1
  }

  comp.series{
    count = count + 1

    @tu.azzert(count == 3)
    @tu.test_complete
  }

  comp.execute

end

def test_with_futures
  comp = Composer.new

  b1_executed = b2_executed = b3_executed = false

  f1 = SimpleFuture.new
  b1 = Proc.new{
    b1_executed = true
    f1
  }

  f2 = SimpleFuture.new
  b2 = Proc.new{
    b2_executed = true
    f2
  }

  f3 = SimpleFuture.new
  b3 = Proc.new{
    b3_executed = true
    f3
  }

  comp.series(b1)
  comp.series(b2)
  comp.series(b3)

  comp.execute

  @tu.azzert(b1_executed)
  @tu.azzert(!b2_executed)
  @tu.azzert(!b3_executed)

  @tu.azzert(!f1.complete?)
  @tu.azzert(!f2.complete?)
  @tu.azzert(!f3.complete?)

  f1.result = nil

  @tu.azzert(b1_executed)
  @tu.azzert(b2_executed)
  @tu.azzert(!b3_executed)

  @tu.azzert(f1.complete?)
  @tu.azzert(!f2.complete?)
  @tu.azzert(!f3.complete?)

  f2.result = nil

  @tu.azzert(b1_executed)
  @tu.azzert(b2_executed)
  @tu.azzert(b3_executed)

  @tu.azzert(f1.complete?)
  @tu.azzert(f2.complete?)
  @tu.azzert(!f3.complete?)

  f3.result = nil

  @tu.azzert(b1_executed)
  @tu.azzert(b2_executed)
  @tu.azzert(b3_executed)

  @tu.azzert(f1.complete?)
  @tu.azzert(f2.complete?)
  @tu.azzert(f3.complete?)

  @tu.test_complete

end

def vertx_stop
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
