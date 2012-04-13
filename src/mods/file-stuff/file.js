load('vertx.js')

vertx.fileSystem.copy("from.txt", "to.txt", function(err, res) {
  if (err) {
    stdout.println("Failed to copy");
  } else {
    stdout.println("Copied ok");
  }
});