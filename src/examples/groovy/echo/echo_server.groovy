import org.nodex.groovy.core.net.NetServer
import org.nodex.groovy.core.Nodex

println("Hello world")

Nodex.go({ x ->
  new NetServer().connectHandler({ socket ->
    socket.dataHandler({ data ->
      println("Got data " + data)
      socket.write(data)
    })
  }).listen(8080)
})


println("Hit enter to exit")
System.in.read()

