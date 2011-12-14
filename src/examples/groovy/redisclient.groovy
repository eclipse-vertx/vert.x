import java.util.concurrent.Executors

def n = 5000
def c = { new URL("http://localhost:8080/").text }
def executor = Executors.newFixedThreadPool(50)
executor.invokeAll((0..<n).collect { c })

executor.shutdown()

println "Final output: ${new URL("http://localhost:8080/").text}"
