import org.nodex.groovy.core.net.NetServer
import org.nodex.groovy.core.Nodex

println("Hello world")

Nodex.go({ x ->
    server = new NetServer()
    server.connectHandler({ socket ->
        socket.dataHandler({ data ->
            println("Got data " + data)
            socket.write(data)
        })
    })
    server.listen(8080)
})


println("Hit enter to exit")
System.in.read()

