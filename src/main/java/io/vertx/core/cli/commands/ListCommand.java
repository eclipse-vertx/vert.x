package io.vertx.core.cli.commands;

import io.vertx.core.cli.CommandLineException;
import io.vertx.core.cli.DefaultCommand;
import io.vertx.core.cli.Description;
import io.vertx.core.cli.Summary;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command listing launched vert.x instances. Instances are found using the `vertx.id` indicator in the
 * process list.
 */
@Summary("List vert.x applications")
@Description("List all vert.x application launched with the `start` command")
public class ListCommand extends DefaultCommand {


  private final static Pattern PS = Pattern.compile("-Dvertx.id=(.*)\\s*");

  @Override
  public String name() {
    return "list";
  }

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  @Override
  public void run() throws CommandLineException {
    out.println("Listing vert.x applications...");
    List<String> cmd = new ArrayList<>();

    if (!ExecUtils.isWindows()) {
      try {
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("ps ax | grep \"vertx.id=\"");

        final Process process = new ProcessBuilder(cmd).start();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
          final Matcher matcher = PS.matcher(line);
          if (matcher.find()) {
            out.println(matcher.group(1));
          }
        }
        process.waitFor();
      } catch (Exception e) {
        e.printStackTrace(out);
      }

    } else {
      try {

        // Use wmic.
        cmd.add("WMIC");
        cmd.add("PROCESS");
        cmd.add("WHERE");
        cmd.add("CommandLine like '%java.exe%'");
        cmd.add("GET");
        cmd.add("CommandLine");
        cmd.add("/VALUE");

        final Process process = new ProcessBuilder(cmd).start();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
          final Matcher matcher = PS.matcher(line);
          if (matcher.find()) {
            out.println(matcher.group(1));
          }
        }
        process.waitFor();
      } catch (Exception e) {
        e.printStackTrace(out);
      }
    }
  }
}
