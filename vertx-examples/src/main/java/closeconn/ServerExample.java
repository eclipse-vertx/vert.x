import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.deploy.Verticle;

public class ServerExample extends Verticle {

    public void start() {
        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest req) {
                System.out.println("Got request: " + req.uri);
                System.out.println("Headers are: ");
                for (String key : req.headers().keySet()) {
                    System.out.println(key + ":" + req.headers().get(key));
                }
                //Perform some async processing,
                //i.e. call remote service or access the database
                Thread thread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        process(req);
                    }
                });
                thread.start();
            }
        }).listen(8081);
    }

    public void process(final HttpServerRequest req) {
        //Write response in vert.x event loop
        vertx.runOnLoop(new Handler<Void>() {

            @Override
            public void handle(Void event) {
                req.response.headers().put("Content-Type", "text/html; charset=UTF-8");
                req.response.end("<html><body><h1>Hello from vert.x!</h1></body></html>");
            }
        });
    }
}