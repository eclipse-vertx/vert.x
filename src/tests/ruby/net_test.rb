require 'test/unit'

class NetTest < Test::Unit::TestCase
  def setup
    puts "Setup called"
  end

  def teardown
    puts "teardown called"
  end

  def test_fail
    #assert(false, 'Assertion was false.')
  end
end