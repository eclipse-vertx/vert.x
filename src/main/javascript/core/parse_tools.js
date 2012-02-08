var vertx = vertx || {};

if (!vertx.DelimitedParser) {
  vertx.DelimitedParser = function(delim, output) {
    return new org.vertx.java.core.parsetools.RecordParser.newDelimited(delim, output);
  }

  vertx.FixedParser = function(size, output) {
    return new org.vertx.java.core.parsetools.RecordParser.newFixed(size, output);
  }
}
