class Foo
  def Foo.func1(tu)
    require('scriptloading/script2')
    tu.azzert(Bar.func2 == 'bar')
    "foo"
  end
end