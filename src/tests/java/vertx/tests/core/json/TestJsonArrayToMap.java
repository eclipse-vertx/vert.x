package vertx.tests.core.json;

import org.junit.Test;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

/**
 * Test for toMap issue in JsonArray
 *
 * @author Adam Hathcock, <a href="mailto:adam@hathcock.co.uk">adam@hathcock.co.uk</a>
 */
public final class TestJsonArrayToMap {

    @Test
    public void expectJsonArrayToClone() {
        JsonArray array = new JsonArray();
        array.add("test");
        JsonObject object = new JsonObject();
        object.putArray("array", array);

        //want to clone
        JsonObject object2 = new JsonObject(object.toMap());
        //this shouldn't throw an exception, it does before patch
        JsonArray array2 = object2.getArray("array");

    }
}

