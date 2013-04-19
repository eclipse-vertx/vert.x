import org.vertx.java.core.json.*;
import java.math.BigDecimal;
import java.util.*;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.EventBus;

public class StaticData extends Verticle {
	String  pa = "vertx.mongopersistor";
		
	public void start() {
		
		List<Map<String,Object>> albums = getAlbums();
		
		EventBus eb = vertx.eventBus();
		eb.send(pa, new JsonObject("{\"action\": \"delete\", \"collection\": \"albums\", \"matcher\": {}}"));
		eb.send(pa, new JsonObject("{\"action\": \"delete\", \"collection\": \"users\", \"matcher\": {}}"));
		
		for (Map<String,Object> album : albums) {
			eb.send(pa, new JsonObject("{\"action\": \"save\", \"collection\": \"albums\", \"document\": "+new JsonObject(album).toString()+"}"));
		}
		eb.send(pa, new JsonObject("{\"action\": \"save\", \"collection\": \"users\", \"document\": {\"firstname\": \"Tim\", \"lastname\": \"Fox\", \"email\": \"tim@localhost.com\", \"username\": \"tim\", \"password\": \"password\"}}"));

    }
	
	private List<Map<String,Object>> getAlbums() {
		List<Map<String,Object>> albums = new ArrayList<>();
		
		Map<String, Object> map = new HashMap<>();
		map.put("artist", "The Wurzels");
		map.put("genre", "Scrumpy and Western");
		map.put("title", "I Am A Cider Drinker");
		map.put("price", new BigDecimal("0.99"));
		albums.add(map);
		
		map = new HashMap<>();
		map.put("artist", "Vanilla Ice");
		map.put("genre", "Hip Hop");
		map.put("title", "Ice Ice Baby");
		map.put("price", new BigDecimal("0.01"));
		albums.add(map);
		
		map = new HashMap<>();
		map.put("artist", "Ena Baga");
		map.put("genre", "Easy Listening");
		map.put("title", "The Happy Hammond");
		map.put("price", new BigDecimal("0.50"));
		albums.add(map);
		
		map = new HashMap<>();
		map.put("artist", "The Tweets");
		map.put("genre", "Bird related songs");
		map.put("title", "The Birdy Song");
		map.put("price", new BigDecimal("1.20"));
		albums.add(map);
		
		return albums;
	}
} 
