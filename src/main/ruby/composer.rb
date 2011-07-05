class Composer
  def Composer.compose
    Composer.new
  end

  def initialize
  end

  def parallel(*composables)
    self
  end

  def then(composable)
    self
  end

  private :initialize
end

class Composable

  def initialize(&block)
    @perform_block = block
  end

  def run
    @perform_block.call
  end

end

class Action < Composable

  def set_result(result)
    puts "setting result #{result}"
    @result = result
  end

  def result
    @result
  end

  def run
    @perform_block.call(self)
  end

end

class RedisExample
  def RedisExample.lookup(key, &block)
    block.call(1234)
  end
end

composer = Composer.compose

action1 = Action.new { |action|
  RedisExample.lookup(343) { |value|
    action.set_result(value)
  }
}

action2 = Action.new { |action|
  RedisExample.lookup(8786) { |value|
    action.set_result(value)
  }
}


action3 = Composable.new { puts "result is #{action1.result + action2.result}" }

action1.run
action2.run
action3.run

#or something like
Composer.compose.parallel(Action.new { |action| RedisExample.lookup(343) { |value| action.set_result(value) } },
                          Action.new { |action| RedisExample.lookup(76776) { |value| action.set_result(value) } }).
    then(Composable.new { puts "result is #{action1.result + action2.result}" })








