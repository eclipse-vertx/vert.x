print "hello"


def call_it(f):
  f()

def bar():
 print "in bar"


call_it(bar)




call_it(lambda: print "in lambda")