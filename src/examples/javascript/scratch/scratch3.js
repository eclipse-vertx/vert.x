


/*

public class PassBack {

  private Handler<Object> handler;

  public void passBack(Object obj) {
    System.out.println("In Java, passing object back to JS");
    handler.handle(obj);
  }

  public void start(Handler<Object> handler) {
    System.out.println("Starting");
    this.handler = handler;
    handler.handle(new Object());
  }
}

 */


var passback = new org.vertx.PassBack();

var count = 0;

var handler = function(obj) {

  if (count > 0) {
    // function foo should now exist in obj
    obj.foo();
  }

  var proto = {
    foo: function() {
      log.println("in foo!");
    }
  }

  obj.__proto__ = proto;

  obj.foo();  // This works initially

  count++;

  passback.passBack(obj);

};

passback.start(handler);
