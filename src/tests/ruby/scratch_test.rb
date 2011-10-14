require 'test/unit'

require 'vertx'
include Vertx

class ScratchTest < Test::Unit::TestCase

  class Foo

    def initialize(&blk)
      @blk = blk
    end

    def wibble
      @blk.call
    end

    def hello
      puts "hello called"
    end
  end

  def test_foo

    f = Foo.new { hello }

    f.wibble

  end


end