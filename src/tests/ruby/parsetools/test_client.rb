require "vertx"
include Vertx
require "test_utils"

@tu = TestUtils.new

def test_delimited
  str = ""
  iters = 100
  for i in 0..iters - 1 do
    str << "line #{i}"
    str << "\n" if i != iters - 1
  end

  lines = []
  parser = RecordParser::new_delimited("\n") { |line|
    lines << line
  }

  parser.input(Buffer.create_from_str(str))

  count = 0
  lines.each { |line|
    @tu.azzert("line #{count}" == line.to_s)
    count += 1
  }

  @tu.test_complete
end

def vertx_stop
  @tu.unregister_all
  @tu.app_stopped
end

@tu.register_all(self)
@tu.app_ready
