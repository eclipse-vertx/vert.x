package deploymod

println "Deploying module"

conf = ["some-var" : "hello"]

container.deployModule("org.foo.MyMod-v1.0", conf, 1) { deploymentID ->
  println "This gets called when deployment is complete, deployment id is $deploymentID"
}