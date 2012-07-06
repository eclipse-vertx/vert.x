(function(global) {

Object.defineProperties(global, {
  global: {
    value: global
  },
  load: {
    value: org.vertx.java.deploy.impl.rhino.RhinoVerticle.load
  },
  stdin: {
    value: java.lang.System.in
  },
  stdout: {
    value: java.lang.System.out
  },
  stderr: {
    value: java.lang.System.err
  }
});

})(this);
