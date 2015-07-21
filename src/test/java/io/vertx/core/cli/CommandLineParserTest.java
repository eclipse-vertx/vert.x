package io.vertx.core.cli;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineParserTest {

  private CommandLine commandLine;

  @Before
  public void setUp() {
    commandLine = new CommandLine();
  }

  @Test
  public void testWithOneLongOption() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").acceptValue().type(String.class).build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "--file=hello.txt");
    assertThat(commandLine.getOptions()).hasSize(1);
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(commandLine.getOptionValues("f")).containsExactly("hello.txt");
  }

  @Test
  public void testWithOneLongOptionUsingSpace() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").acceptValue().type(String.class).build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "--file", "hello.txt");
    assertThat(commandLine.getOptions()).hasSize(1);
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(commandLine.getOptionValues("f")).containsExactly("hello.txt");
  }

  @Test
  public void testWithOneShortOption() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").acceptValue().type(String.class).build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "-f=hello.txt");
    assertThat(commandLine.getOptions()).hasSize(1);
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(commandLine.getOptionValues("f")).containsExactly("hello.txt");
  }

  @Test
  public void testWithOneShortOptionUsingSpace() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").acceptValue().type(String.class).build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "-f", "hello.txt");
    assertThat(commandLine.getOptions()).hasSize(1);
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(commandLine.getOptionValues("f")).containsExactly("hello.txt");
  }

  @Test
  public void testTheDifferentFormatForLongOption() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").acceptValue().type(String.class).build()
    };
    commandLine.addOptions(Arrays.asList(options));

    parser.parse(commandLine, "--file", "hello.txt");
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");

    parser.parse(commandLine, "--file=hello.txt");
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");

    parser.parse(commandLine, "-filehello.txt");
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");

    parser.parse(commandLine, "--FILE", "hello.txt");
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");
  }

  @Test
  public void testTheDifferentFormatForShortOption() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").acceptValue().type(String.class).build()
    };
    commandLine.addOptions(Arrays.asList(options));

    parser.parse(commandLine, "-f", "hello.txt");
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");

    parser.parse(commandLine, "-f=hello.txt");
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");

    parser.parse(commandLine, "-fhello.txt");
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");
  }

  @Test
  public void testWithMultipleValues() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").acceptMultipleValues().type(String.class).build()
    };
    commandLine.addOptions(Arrays.asList(options));

    parser.parse(commandLine, "-f=hello.txt", "--file=hello2.txt");
    assertThat(commandLine.getOptions()).hasSize(1);
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(commandLine.getOptionValues("f")).containsExactly("hello.txt", "hello2.txt");
  }

  @Test
  public void testWithList() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").list().type(String.class).build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "-f=hello.txt,hello2.txt");
    assertThat(commandLine.getOptions()).hasSize(1);
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(commandLine.getOptionValues("f")).containsExactly("hello.txt", "hello2.txt");
  }

  @Test
  public void testWithFlag() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<Boolean>builder().shortName("f").longName("flag").type(Boolean.class).acceptValue().build(),
        OptionModel.<Boolean>builder().shortName("f2").longName("flag2").type(Boolean.TYPE).acceptValue().build(),
        OptionModel.<Boolean>builder().shortName("f3").longName("flag3").type(Boolean.class).acceptValue(false).build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "--flag", "--flag2", "--flag3");
    assertThat((boolean) commandLine.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) commandLine.getOptionValue("flag2")).isEqualTo(true);
    assertThat((boolean) commandLine.getOptionValue("flag3")).isEqualTo(true);

    parser.parse(commandLine, "--flag=true", "--flag2=false", "--flag3");
    assertThat((boolean) commandLine.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) commandLine.getOptionValue("flag2")).isEqualTo(false);
    assertThat((boolean) commandLine.getOptionValue("flag3")).isEqualTo(true);

    parser.parse(commandLine, "--flag", "--flag2");
    assertThat((boolean) commandLine.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) commandLine.getOptionValue("flag2")).isEqualTo(true);
    assertThat((boolean) commandLine.getOptionValue("flag3")).isEqualTo(false);

    parser.parse(commandLine, "--flag", "true", "--flag2", "false", "--flag3");
    assertThat((boolean) commandLine.getOptionValue("flag")).isEqualTo(true);
    assertThat((boolean) commandLine.getOptionValue("flag2")).isEqualTo(false);
    assertThat((boolean) commandLine.getOptionValue("flag3")).isEqualTo(true);
  }

  @Test
  public void testArguments() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<Boolean>builder().shortName("f").longName("flag").type(Boolean.class).acceptValue().build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "org.acme.Foo", "-f=no");
    assertThat(commandLine.getAllArguments()).contains("org.acme.Foo");

    parser.parse(commandLine, "-f=no", "org.acme.Foo");
    assertThat(commandLine.getAllArguments()).contains("org.acme.Foo");

    parser.parse(commandLine, "-f=no", "org.acme.Foo", "bar");
    assertThat(commandLine.getAllArguments()).contains("org.acme.Foo", "bar");
  }

  @Test
  public void testUnknownOption() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<Boolean>builder().shortName("f").longName("flag").type(Boolean.class).isRequired().acceptValue().build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "-flag=true", "-unknown=x");
    assertThat(commandLine.getAllArguments()).contains("-unknown=x");
  }

  @Test(expected = MissingOptionException.class)
  public void testNotFullfilledRequiredOptions() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<Boolean>builder().shortName("f").longName("flag").type(Boolean.class).isRequired().acceptValue().build()
    };
    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine);
  }

  @Test
  public void testRequiredOptions() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<Boolean>builder().shortName("f").longName("flag").type(Boolean.class).isRequired().acceptValue().build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "-f");

  }

  @Test
  public void testQuotedValues() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").acceptValue().type(String.class).build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "--file", "\"hello.txt\"");
    assertThat(commandLine.getOptions()).hasSize(1);
    assertThat((String) commandLine.getOptionValue("file")).isEqualTo("hello.txt");
    assertThat(commandLine.getOptionValues("f")).containsExactly("hello.txt");
  }

  @Test
  public void testNegativeNumbers() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<Double>builder().longName("num").acceptValue().type(Double.class).build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "--num", "-1.5");
    assertThat(commandLine.getOptions()).hasSize(1);
    assertThat(commandLine.getArguments()).isEmpty();
    assertThat((double) commandLine.getOptionValue("num")).isEqualTo(-1.5d);

    parser.parse(commandLine, "--num=-1.5");
    assertThat(commandLine.getOptions()).hasSize(1);
    assertThat(commandLine.getArguments()).isEmpty();
    assertThat((double) commandLine.getOptionValue("num")).isEqualTo(-1.5d);
  }

  @Test(expected = MissingValueException.class)
  public void testMissingValue() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("f").longName("file").type(String.class).acceptValue().build()
    };

    commandLine.addOptions(Arrays.asList(options));
    parser.parse(commandLine, "--file");
  }

  @Test
  public void testVertxRun() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().longName("conf").type(String.class).acceptValue().build(),
        OptionModel.<Integer>builder().longName("instances").type(Integer.class).acceptValue().defaultValue("1").build(),
        OptionModel.<Boolean>builder().longName("worker").type(Boolean.class).acceptValue(false).build(),
        OptionModel.<String>builder().longName("classpath").shortName("cp").listSeparator(File.pathSeparator)
            .type(String.class).acceptValue().build(),
        OptionModel.<Boolean>builder().longName("cluster").type(Boolean.class).acceptValue(false).build(),
        OptionModel.<Integer>builder().longName("cluster-port").type(Integer.class).acceptValue().build(),
        OptionModel.<String>builder().longName("cluster-host").type(String.class).acceptValue().build(),
        OptionModel.<Boolean>builder().longName("ha").type(Boolean.class).acceptValue().build(),
        OptionModel.<Integer>builder().longName("quorum").type(Integer.class).acceptValue().build(),
        OptionModel.<String>builder().longName("ha-group").type(String.class).defaultValue("__DEFAULT__")
            .acceptValue().build(),
    };
    commandLine.addOptions(Arrays.asList(options));
    commandLine.addArgument(ArgumentModel.<String>builder().argName("verticle").index(0).type(String.class).build());

    // Bare
    parser.parse(commandLine, "-ha");
    assertThat((boolean) commandLine.getOptionValue("ha")).isTrue();
    assertThat((String) commandLine.getArgumentValue("verticle")).isNull();
    assertThat((String) commandLine.getArgumentValue(0)).isNull();
    commandLine = parser.parse(commandLine, "-ha", "true");
    assertThat((boolean) commandLine.getOptionValue("ha")).isTrue();
    commandLine = parser.parse(commandLine, "-ha=true");
    assertThat((boolean) commandLine.getOptionValue("ha")).isTrue();
    commandLine = parser.parse(commandLine, "--ha");
    assertThat((boolean) commandLine.getOptionValue("ha")).isTrue();
    commandLine = parser.parse(commandLine, "--ha", "false");
    assertThat((boolean) commandLine.getOptionValue("ha")).isFalse();
    commandLine = parser.parse(commandLine, "--ha=no");
    assertThat((boolean) commandLine.getOptionValue("ha")).isFalse();


    // Verticle deployment
    commandLine = parser.parse(commandLine, "org.acme.FooVerticle", "-instances=4", "-cp", "." + File.pathSeparator + "my" +
        ".jar");
    assertThat(commandLine.getAllArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    int instances = commandLine.getOptionValue("instances");
    List<String> classpath = commandLine.getOptionValues("classpath");
    assertThat(instances).isEqualTo(4);
    assertThat(classpath).containsExactly(".", "my.jar");


    // Cluster environment
    commandLine = parser.parse(commandLine, "org.acme.FooVerticle", "-cluster");
    assertThat(commandLine.getAllArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) commandLine.getOptionValue("cluster")).isTrue();

    commandLine = parser.parse(commandLine, "org.acme.FooVerticle", "--cluster");
    assertThat(commandLine.getAllArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) commandLine.getOptionValue("cluster")).isTrue();

    commandLine = parser.parse(commandLine, "org.acme.FooVerticle", "-cluster", "-cluster-host", "127.0.0.1");
    assertThat(commandLine.getAllArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) commandLine.getOptionValue("cluster")).isTrue();
    assertThat((String) commandLine.getOptionValue("cluster-host")).isEqualTo("127.0.0.1");

    commandLine = parser.parse(commandLine, "org.acme.FooVerticle", "-cluster", "--cluster-host", "127.0.0.1");
    assertThat(commandLine.getAllArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) commandLine.getOptionValue("cluster")).isTrue();
    assertThat((String) commandLine.getOptionValue("cluster-host")).isEqualTo("127.0.0.1");

    commandLine = parser.parse(commandLine, "org.acme.FooVerticle", "-cluster", "-cluster-host=127.0.0.1");
    assertThat(commandLine.getAllArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) commandLine.getOptionValue("cluster")).isTrue();
    assertThat((String) commandLine.getOptionValue("cluster-host")).isEqualTo("127.0.0.1");

    commandLine = parser.parse(commandLine, "org.acme.FooVerticle", "-cluster", "-cluster-host", "127.0.0.1",
        "-cluster-port", "1234");
    assertThat(commandLine.getAllArguments()).hasSize(1).containsExactly("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue("verticle")).isEqualTo("org.acme.FooVerticle");
    assertThat((String) commandLine.getArgumentValue(0)).isEqualTo("org.acme.FooVerticle");
    assertThat((boolean) commandLine.getOptionValue("cluster")).isTrue();
    assertThat((String) commandLine.getOptionValue("cluster-host")).isEqualTo("127.0.0.1");
    assertThat((int) commandLine.getOptionValue("cluster-port")).isEqualTo(1234);

  }

  @Test
  public void testWithDashD() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    OptionModel[] options = new OptionModel[]{
        OptionModel.<String>builder().shortName("D").longName("systemProperty").acceptMultipleValues()
            .type(String.class).build(),
        OptionModel.<Boolean>builder().shortName("F").longName("flag").type(Boolean.class).build()
    };

    commandLine.addOptions(Arrays.asList(options));
    commandLine = parser.parse(commandLine, "-Dx=y", "-F");
    assertThat(commandLine.getOptions()).hasSize(2);
    assertThat((String) commandLine.getOptionValue("systemProperty")).isEqualTo("x=y");
    assertThat((boolean) commandLine.getOptionValue("flag")).isTrue();
  }
}