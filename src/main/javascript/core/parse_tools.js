var vertx = vertx || {};

vertx.parsetools = vertx.parsetools || {};

vertx.parsetools.DelimitedParser = function(delim, output) {
  return new org.vertx.java.core.parsetools.RecordParser.newDelimited(delim, output);
}

vertx.parsetools.FixedParser = function(size, output) {
  return new org.vertx.java.core.parsetools.RecordParser.newFixed(size, output);
}
