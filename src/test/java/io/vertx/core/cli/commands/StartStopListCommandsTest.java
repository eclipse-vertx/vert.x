package io.vertx.core.cli.commands;

import io.vertx.core.Launcher;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check the behavior of the start, stop and list commands.
 */
public class StartStopListCommandsTest extends CommandTestBase {

  @Before
  public void setUp() throws IOException {
    File manifest = new File("target/test-classes/META-INF/MANIFEST.MF");
    if (manifest.isFile()) {
      manifest.delete();
    }

    super.setUp();
  }

  @Test
  public void testStartListStop() throws InterruptedException {
    record();

    cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
        "--launcher-class", Launcher.class.getName()});

    waitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        // Ignore it.
      }
      return false;
    });
    assertThat(output.toString()).contains("Starting vert.x application");

    output.reset();
    cli.dispatch(new String[]{"list"});
    assertThat(output.toString()).hasLineCount(2);

    // Extract id.
    String[] lines = output.toString().split(System.lineSeparator());
    String id = lines[1];
    output.reset();
    cli.dispatch(new String[]{"stop", id});
    assertThat(output.toString())
        .contains("Stopping vert.x application '" + id + "'")
        .contains("Application '" + id + "' stopped");

    waitUntil(() -> {
      try {
        getHttpCode();
      } catch (IOException e) {
        return true;
      }
      return false;
    });

    waitUntil(() -> {
      output.reset();
      cli.dispatch(new String[]{"list"});
      return ! output.toString().contains(id);
    });

    assertThat(output.toString()).hasLineCount(1);
  }

  @Test
  public void testStartListStopWithId() throws InterruptedException, IOException {
    record();

    cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
        "--launcher-class", Launcher.class.getName(), "--vertx.id=hello"});


    waitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        // Ignore it.
      }
      return false;
    });


    assertThat(output.toString()).contains("Starting vert.x application").contains("hello");

    output.reset();
    cli.dispatch(new String[]{"list"});
    assertThat(output.toString()).hasLineCount(2).contains("hello");

    // Extract id.
    String[] lines = output.toString().split(System.lineSeparator());
    String id = lines[1];
    assertThat(id).isEqualToIgnoringCase("hello");
    output.reset();
    cli.dispatch(new String[]{"stop", id});
    assertThat(output.toString())
        .contains("Stopping vert.x application '" + id + "'")
        .contains("Application '" + id + "' stopped");

    waitUntil(() -> {
      try {
        getHttpCode();
      } catch (IOException e) {
        return true;
      }
      return false;
    });

    waitUntil(() -> {
      output.reset();
      cli.dispatch(new String[]{"list"});
      return ! output.toString().contains(id);
    });

    assertThat(output.toString()).hasLineCount(1);
  }

  @Test
  public void testStartListStopWithIdAndAnotherArgument() throws InterruptedException, IOException {
    record();

    cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
        "--launcher-class", Launcher.class.getName(), "--vertx.id=hello", "-cluster"});

    waitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        // Ignore it.
      }
      return false;
    });
    assertThat(output.toString()).contains("Starting vert.x application").contains("hello");
    assertThat(getContent().getBoolean("clustered")).isTrue();

    output.reset();
    cli.dispatch(new String[]{"list"});
    assertThat(output.toString()).hasLineCount(2).contains("hello");

    // Extract id.
    String[] lines = output.toString().split(System.lineSeparator());
    String id = lines[1];
    assertThat(id).isEqualToIgnoringCase("hello");
    output.reset();
    cli.dispatch(new String[]{"stop", id});
    assertThat(output.toString())
        .contains("Stopping vert.x application '" + id + "'")
        .contains("Application '" + id + "' stopped");

    waitUntil(() -> {
      try {
        getHttpCode();
      } catch (IOException e) {
        return true;
      }
      return false;
    });

    waitUntil(() -> {
      output.reset();
      cli.dispatch(new String[]{"list"});
      return ! output.toString().contains(id);
    });

    assertThat(output.toString()).hasLineCount(1);
  }

  @Test
  public void testStartListStopWithIdAndAnotherArgumentBeforeId() throws InterruptedException, IOException {
    record();

    cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
        "--launcher-class", Launcher.class.getName(), "-cluster", "--vertx.id=hello"});

    waitUntil(() -> {
      try {
        return getHttpCode() == 200;
      } catch (IOException e) {
        // Ignore it.
      }
      return false;
    });
    assertThat(output.toString()).contains("Starting vert.x application").contains("hello");
    assertThat(getContent().getBoolean("clustered")).isTrue();

    output.reset();
    cli.dispatch(new String[]{"list"});
    assertThat(output.toString()).hasLineCount(2).contains("hello");

    // Extract id.
    String[] lines = output.toString().split(System.lineSeparator());
    String id = lines[1];
    assertThat(id).isEqualToIgnoringCase("hello");
    output.reset();
    cli.dispatch(new String[]{"stop", id});
    assertThat(output.toString())
        .contains("Stopping vert.x application '" + id + "'")
        .contains("Application '" + id + "' stopped");

    waitUntil(() -> {
      try {
        getHttpCode();
      } catch (IOException e) {
        return true;
      }
      return false;
    });

    waitUntil(() -> {
      output.reset();
      cli.dispatch(new String[]{"list"});
      return ! output.toString().contains(id);
    });

    assertThat(output.toString()).hasLineCount(1);
  }

  private int getHttpCode() throws IOException {
    return ((HttpURLConnection) new URL("http://localhost:8080")
        .openConnection()).getResponseCode();
  }

  private JsonObject getContent() throws IOException {
    URL url = new URL("http://localhost:8080");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.connect();
    InputStreamReader in = new InputStreamReader((InputStream) conn.getContent());
    BufferedReader buff = new BufferedReader(in);
    String line;
    StringBuilder builder = new StringBuilder();
    do {
      line = buff.readLine();
      builder.append(line).append("\n");
    } while (line != null);

    return new JsonObject(builder.toString());
  }

}