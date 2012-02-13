puts "in ruby verticle"

def foo
  bar
end

def bar
  quux
end


def quux
  raise "Foo"
end

foo()

