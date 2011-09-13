require 'test/unit'

class ScratchTest < Test::Unit::TestCase


  module Wibble
    def foo
      self
    end
  end

  class Wobble
    include Wibble
  end

  def test_foo

    w = Wobble.new

    puts "self is #{w.foo}"


  end
end