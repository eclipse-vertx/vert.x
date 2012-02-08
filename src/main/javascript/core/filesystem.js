var vertx = vertx || {};

if (!vertx.FileSystem) {
  vertx.FileSystem = vertx.FileSystem || org.vertx.java.core.file.FileSystem.instance;
}