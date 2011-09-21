require 'test/unit'

require 'nodex'
include Nodex

class ScratchTest < Test::Unit::TestCase

  class Foo
    def wibble
      "wobbled"
    end
  end

  def test_foo

    puts "starting test"

    f1 = Foo.new
    f2 = Foo.new

    puts "f1: #{f1.wibble}"
    puts "f2: #{f2.wibble}"

    class << f2
      Object.remove_method :wibble
    end

    puts "f1: #{f1.wibble}"
    puts "f2: #{f2.wibble}"


    raise "goo"

  end


end