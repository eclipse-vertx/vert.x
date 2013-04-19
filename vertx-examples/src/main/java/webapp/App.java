package webapp;
import org.vertx.java.core.json.*;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;

public class App extends Verticle {
	public void start() {
	
		StringBuffer sb = new StringBuffer();
		sb.append("{");
		// Normal web server stuff
		sb.append(	"\"port\": 8080,");
		sb.append(	"\"host\": \"localhost\",");
		sb.append(	"\"ssl\": true,");
		
		// Configuration for the event bus client side bridge
		// This bridges messages from the client side to the server side event bus
		sb.append(	"\"bridge\": true,");
		
		// This defines which messages from the client we will let through
		// to the server side
		sb.append(	"\"inbound_permitted\": [ {");
		// Allow calls to get static album data from the persistor
		sb.append(		"\"address\" : \"vertx.mongopersistor\",");
		sb.append(		"\"match\" : {");
		sb.append(			"\"action\" : \"find\",");
		sb.append(			"\"collection\" : \"albums\"");
		sb.append(		"}");
		sb.append(	"}, {" );
		// Allow calls to login
		sb.append(	"\"address\": \"vertx.basicauthmanager.login\"" );
		sb.append(	"}, {" );
		// And to place orders
		sb.append(		"\"address\": \"vertx.mongopersistor\"," );
		sb.append(		"\"requires_auth\": true," );
		sb.append(		"\"match\" : {");
		sb.append(			"\"action\" : \"save\",");
		sb.append(			"\"collection\" : \"orders\"");
		sb.append(		"}");
		sb.append(	"} ],");
		// This defines which messages from the server we will let through to the client
		sb.append(	"\"outbound_permitted\": [ {} ]");
		sb.append("}");
		JsonObject webServerConf = new JsonObject(sb.toString());
		// Now we deploy the modules that we need
		
		// Create a handler to populate demo data when the persistor is loaded
		Handler<String> myHandler = new Handler<String>() {
			public void handle(String message) {
				container.deployVerticle("StaticData.java");
			}
		};

		// Deploy a MongoDB persistor module and pass in the handler
		container.deployModule("vertx.mongo-persistor-v1.2", null, 1, myHandler);
		// Deploy an auth manager to handle the authentication
		container.deployModule("vertx.auth-mgr-v1.1");
		
		
		// Start the web server, with the config we defined above
		container.deployModule("vertx.web-server-v1.0", webServerConf);
    }
} 
