/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.Launcher;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check the behavior of the start, stop and list commands.
 *
 * @author Clement Escoffier <clement@apache.org>
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

        waitForStartup();
        assertThat(output.toString()).contains("Starting vert.x application");

        output.reset();
        cli.dispatch(new String[]{"list"});
        assertThat(output.toString()).hasLineCount(2);
        assertThat(output.toString()).contains("\t" + HttpTestVerticle.class.getName());

        // Extract id.
        String[] lines = output.toString().split(System.lineSeparator());
        String id = lines[1].trim().substring(0, lines[1].trim().indexOf("\t"));
        output.reset();
        // pass --redeploy to not call system.exit
        cli.dispatch(new String[]{"stop", id, "--redeploy"});
        assertThat(output.toString())
            .contains("Stopping vert.x application '" + id + "'")
            .contains("Application '" + id + "' terminated with status 0");


        waitForShutdown();

        assertWaitUntil(() -> {
            output.reset();
            cli.dispatch(new String[]{"list"});
            return !output.toString().contains(id);
        });

        assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
    }

    @Test
    public void testStartListStopWithoutCommand() throws InterruptedException {
        record();

        cli.dispatch(new String[]{"start", HttpTestVerticle.class.getName(),
            "--launcher-class", Launcher.class.getName()});

        waitForStartup();
        assertThat(output.toString()).contains("Starting vert.x application");

        output.reset();
        cli.dispatch(new String[]{"list"});
        assertThat(output.toString()).hasLineCount(2);
        assertThat(output.toString()).contains("\t" + HttpTestVerticle.class.getName());

        // Extract id.
        String[] lines = output.toString().split(System.lineSeparator());
        String id = lines[1].trim().substring(0, lines[1].trim().indexOf("\t"));
        output.reset();
        // pass --redeploy to not call system.exit
        cli.dispatch(new String[]{"stop", id, "--redeploy"});
        assertThat(output.toString())
            .contains("Stopping vert.x application '" + id + "'")
            .contains("Application '" + id + "' terminated with status 0");


        waitForShutdown();

        assertWaitUntil(() -> {
            output.reset();
            cli.dispatch(new String[]{"list"});
            return !output.toString().contains(id);
        });

        assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
    }

    @Test
    public void testStartListStopWithJVMOptions() throws InterruptedException, IOException {
        record();

        cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
            "--launcher-class", Launcher.class.getName(), "--java-opts=-Dfoo=bar -Dbaz=bar", "--redirect-output"});

        waitForStartup();
        assertThat(output.toString()).contains("Starting vert.x application");

        JsonObject content = RunCommandTest.getContent();
        assertThat(content.getString("foo")).isEqualToIgnoringCase("bar");
        assertThat(content.getString("baz")).isEqualToIgnoringCase("bar");

        output.reset();
        cli.dispatch(new String[]{"list"});
        assertThat(output.toString()).hasLineCount(2);

        // Extract id.
        String[] lines = output.toString().split(System.lineSeparator());
        String id = lines[1].trim().substring(0, lines[1].trim().indexOf("\t"));
        output.reset();
        // pass --redeploy to not call system.exit
        cli.dispatch(new String[]{"stop", id, "--redeploy"});
        assertThat(output.toString())
            .contains("Stopping vert.x application '" + id + "'")
            .contains("Application '" + id + "' terminated with status 0");

        waitForShutdown();

        assertWaitUntil(() -> {
            output.reset();
            cli.dispatch(new String[]{"list"});
            return !output.toString().contains(id);
        });

        assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
    }

    private void waitForShutdown() {
        assertWaitUntil(() -> {
            try {
                getHttpCode();
            } catch (IOException e) {
                return true;
            }
            return false;
        });
    }

    private void waitForStartup() {
        assertWaitUntil(() -> {
            try {
                return getHttpCode() == 200;
            } catch (IOException e) {
                // Ignore it.
            }
            return false;
        });
    }

    @Test
    public void testStartListStopWithId() throws InterruptedException, IOException {
        record();

        cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
            "--launcher-class", Launcher.class.getName(), "--vertx-id=hello"});
        waitForStartup();
        assertThat(output.toString()).contains("Starting vert.x application").contains("hello");

        output.reset();
        cli.dispatch(new String[]{"list"});
        assertThat(output.toString()).hasLineCount(2).contains("hello");

        // Extract id.
        String[] lines = output.toString().split(System.lineSeparator());
        String id = lines[1].trim().substring(0, lines[1].trim().indexOf("\t"));

        assertThat(id).isEqualToIgnoringCase("hello");
        output.reset();
        // pass --redeploy to not call system.exit
        cli.dispatch(new String[]{"stop", id, "--redeploy"});
        assertThat(output.toString())
            .contains("Stopping vert.x application '" + id + "'")
            .contains("Application '" + id + "' terminated with status 0");

        waitForShutdown();

        assertWaitUntil(() -> {
            output.reset();
            cli.dispatch(new String[]{"list"});
            return !output.toString().contains(id);
        });

        assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
    }

    @Test
    public void testStartListStopWithIdAndAnotherArgument() throws InterruptedException, IOException {
        record();

        cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
            "--launcher-class", Launcher.class.getName(), "--vertx-id=hello", "-cluster"});

        waitForStartup();
        assertThat(output.toString()).contains("Starting vert.x application").contains("hello");
        assertThat(RunCommandTest.getContent().getBoolean("clustered")).isTrue();

        output.reset();
        cli.dispatch(new String[]{"list"});
        assertThat(output.toString()).hasLineCount(2).contains("hello");

        // Extract id.
        String[] lines = output.toString().split(System.lineSeparator());
        String id = lines[1].trim().substring(0, lines[1].trim().indexOf("\t"));
        assertThat(id).isEqualToIgnoringCase("hello");
        output.reset();
        // pass --redeploy to not call system.exit
        cli.dispatch(new String[]{"stop", id, "--redeploy"});
        assertThat(output.toString())
            .contains("Stopping vert.x application '" + id + "'")
            .contains("Application '" + id + "' terminated with status 0");

        waitForShutdown();

        assertWaitUntil(() -> {
            output.reset();
            cli.dispatch(new String[]{"list"});
            return !output.toString().contains(id);
        });

        assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
    }

    @Test
    public void testStartListStopWithIdAndAnotherArgumentBeforeId() throws InterruptedException, IOException {
        record();

        cli.dispatch(new String[]{"start", "run", HttpTestVerticle.class.getName(),
            "--launcher-class", Launcher.class.getName(), "-cluster", "--vertx-id=hello"});

        waitForStartup();
        assertThat(output.toString()).contains("Starting vert.x application").contains("hello");
        assertThat(RunCommandTest.getContent().getBoolean("clustered")).isTrue();

        output.reset();
        cli.dispatch(new String[]{"list"});
        assertThat(output.toString()).hasLineCount(2).contains("hello");

        // Extract id.
        String[] lines = output.toString().split(System.lineSeparator());
        String id = lines[1].trim().substring(0, lines[1].trim().indexOf("\t"));
        assertThat(id).isEqualToIgnoringCase("hello");
        output.reset();
        // pass --redeploy to not call system.exit
        cli.dispatch(new String[]{"stop", id, "--redeploy"});
        assertThat(output.toString())
            .contains("Stopping vert.x application '" + id + "'")
            .contains("Application '" + id + "' terminated with status 0");

        waitForShutdown();

        assertWaitUntil(() -> {
            output.reset();
            cli.dispatch(new String[]{"list"});
            return !output.toString().contains(id);
        });

        assertThat(output.toString()).hasLineCount(2).contains("No vert.x application found");
    }

    private int getHttpCode() throws IOException {
        return ((HttpURLConnection) new URL("http://localhost:8080")
            .openConnection()).getResponseCode();
    }

    @Test
    public void testFatJarExtraction() {
        String command = "java -jar fat.jar -Dvertx.id=xxx --cluster";
        assertThat(ListCommand.extractApplicationDetails(command)).isEqualTo("fat.jar");

        command = "java -jar bin/fat.jar -Dvertx.id=xxx --cluster";
        assertThat(ListCommand.extractApplicationDetails(command)).isEqualTo("bin/fat.jar");

        command = "java foo bar -Dvertx.id=xxx --cluster";
        assertThat(ListCommand.extractApplicationDetails(command)).isEqualTo("");
    }

    @Test
    public void testStoppingAnUnknownProcess() {
        if (ExecUtils.isWindows()) {
            // Test skipped on windows, because on windows we do not check whether or not the pid exists.
            return;
        }
        record();
        String id = "this-process-does-not-exist";
        output.reset();
        // pass --redeploy to not call system.exit
        cli.dispatch(new String[]{"stop", id, "--redeploy"});
        assertThat(output.toString())
            .contains("Stopping vert.x application '" + id + "'")
            .contains("Cannot find process for application using the id");
    }

    @Test
    public void testVerticleExtraction() {
        String command = "vertx run verticle test1 -Dvertx.id=xxx --cluster";
        assertThat(ListCommand.extractApplicationDetails(command)).isEqualTo("verticle");
    }

}
