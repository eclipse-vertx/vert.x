
require 'test/unit'

class ScratchTest < Test::Unit::TestCase

  class SomeClass


    def headers=(key, val)[]
      puts "invoked headers= with #{key} #{val}"
    end

  end

  def test_foo
    sc = SomeClass.new

    sc.headers["foo"] = "bar"


  end
end