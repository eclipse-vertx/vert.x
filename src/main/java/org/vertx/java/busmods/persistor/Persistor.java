package org.vertx.java.busmods.persistor;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;
import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO max batch sizes
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Persistor extends BusModBase {

  private static final Logger log = Logger.getLogger(Persistor.class);

  public Persistor(final String address) {
    super(address, true);
  }

  private Mongo mongo;
  private DB db;

  @Override
  public void start() {
    try {
      mongo = new Mongo("localhost", 27017 );
      db = mongo.getDB("mydb");
      super.start();
    } catch (UnknownHostException e) {
      log.error("Failed to connect to mongo server", e);
    }
  }

  @Override
  public void stop() {
    super.stop();
    mongo.close();
  }

  public void handle(Message message, Map<String, Object> json) {
    String action = (String)json.get("action");

    if (action == null) {
      sendError(message, "action must be specified");
      return;
    }

    switch (action) {
      case "save":
        doSave(message, json);
        break;
      case "find":
        doFind(message, json);
        break;
      case "findone":
        doFindOne(message, json);
        break;
      case "delete":
        doDelete(message, json);
        break;
      default:
        sendError(message, "Invalid action: " + action);
        return;
    }
  }

  private void doSave(Message message, Map<String, Object> json) {
    String collection = (String)getMandatory("collection", message, json);
    if (collection == null) {
      return;
    }
    Object doc = getMandatory("document", message, json);
    if (doc == null) {
      return;
    }
    DBCollection coll = db.getCollection(collection);
    coll.save(jsonToDBObject(doc));
    sendOK(message);
  }

  private void doFind(Message message, Map<String, Object> json) {
    String collection = (String)getMandatory("collection", message, json);
    if (collection == null) {
      return;
    }
    Integer limit = (Integer)json.get("limit");
    if (limit == null) {
      limit = -1;
    }
    Object matcher = getMandatory("matcher", message, json);
    if (matcher == null) {
      return;
    }
    Object sort = json.get("sort");
    DBCollection coll = db.getCollection(collection);
    DBCursor cursor = coll.find(jsonToDBObject(matcher));
    if (limit != -1) {
      cursor.limit(limit);
    }
    if (sort != null) {
      cursor.sort(jsonToDBObject(sort));
    }
    Map<String, Object> reply = new HashMap<>();
    List results = new ArrayList();
    while (cursor.hasNext()) {
      DBObject obj = cursor.next();
      String s = obj.toString();
      Map<String, Object> m = helper.stringToJson(s);
      results.add(m);
    }
    cursor.close();
    reply.put("results", results);
    sendOK(message, reply);
  }

  private void doFindOne(Message message, Map<String, Object> json) {
    String collection = (String)getMandatory("collection", message, json);
    if (collection == null) {
      return;
    }
    Object matcher = json.get("matcher");
    DBCollection coll = db.getCollection(collection);
    DBObject res;
    if (matcher == null) {
      res = coll.findOne();
    } else {
      res = coll.findOne(jsonToDBObject(matcher));
    }
    Map<String, Object> reply = new HashMap<>();
    String s = res.toString();
    Map<String, Object> m = helper.stringToJson(s);
    reply.put("result", m);
    sendOK(message, reply);
  }

  private void doDelete(Message message, Map<String, Object> json) {
    String collection = (String)getMandatory("collection", message, json);
    if (collection == null) {
      return;
    }
    Object matcher = getMandatory("matcher", message, json);
    if (matcher == null) {
      return;
    }
    DBCollection coll = db.getCollection(collection);
    WriteResult res = coll.remove(jsonToDBObject(matcher));
    int deleted = res.getN();
    Map<String, Object> reply = new HashMap<>();
    reply.put("number", deleted);
    sendOK(message, reply);
  }

  private DBObject jsonToDBObject(Object json) {
    String str = helper.jsonToString(json);
    return (DBObject)JSON.parse(str);
  }

}
