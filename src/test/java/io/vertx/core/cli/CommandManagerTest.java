package io.vertx.core.cli;


import io.vertx.core.cli.commands.HelloCommand;
import io.vertx.core.cli.converters.*;
import io.vertx.core.spi.Command;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class CommandManagerTest {

  @Test
  public void testHelloCommandDefinition() {
    Command command = new HelloCommand();
    CommandLine commandLine = new CommandLine();
    CommandManager.define(command, commandLine);

    assertThat(commandLine.getOptions()).hasSize(3);
    OptionModel option = find(commandLine.getOptions(), "name");
    assertThat(option.getLongName()).isEqualToIgnoringCase("name");
    assertThat(option.getShortName()).isEqualToIgnoringCase("n");
    assertThat(option.getType()).isEqualTo(String.class);
    assertThat(option.getArgName()).isEqualTo("name");
    assertThat(option.getDescription()).isEqualToIgnoringCase("your name");
    assertThat(option.getDefaultValue()).isNull();
    assertThat(option.acceptValue()).isTrue();
    assertThat(option.acceptMultipleValues()).isFalse();
    assertThat(option.isRequired()).isTrue();
  }

  @Test
  public void testOptionsWithDefaultValue() {
    Command command = new DefaultCommand() {

      @Option(longName = "option", shortName = "o")
      @DefaultValue("bar")
      public void setFoo(String foo) {
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public void run() throws CommandLineException {
      }
    };

    CommandLine commandLine = new CommandLine();
    CommandManager.define(command, commandLine);

    assertThat(commandLine.getOptions()).hasSize(3);
    assertThat(find(commandLine.getOptions(), "option").getDefaultValue()).isEqualTo("bar");
  }

  @Test
  public void testOptionsWithDescription() {
    Command command = new DefaultCommand() {

      @Option(longName = "option", shortName = "o")
      @Description("This option is awesome")
      public void setFoo(String foo) {
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public void run() throws CommandLineException {
      }
    };

    CommandLine commandLine = new CommandLine();
    CommandManager.define(command, commandLine);

    assertThat(commandLine.getOptions()).hasSize(3);
    assertThat(find(commandLine.getOptions(), "option").getDescription()).isEqualTo("This option is awesome");
  }

  @Test
  public void testOptionsParsedAsList() {
    Command command = new DefaultCommand() {

      @Option(longName = "option", shortName = "o")
      @ParsedAsList(separator = ":")
      public void setFoo(List<String> foo) {
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public void run() throws CommandLineException {
      }
    };

    CommandLine commandLine = new CommandLine();
    CommandManager.define(command, commandLine);

    assertThat(find(commandLine.getOptions(), "option").getListSeparator()).isEqualTo(":");
    assertThat(find(commandLine.getOptions(), "option").getListSeparator()).isEqualTo(":");
    assertThat(find(commandLine.getOptions(), "option").acceptMultipleValues()).isTrue();
    assertThat(find(commandLine.getOptions(), "option").getType()).isEqualTo(String.class);
  }

  @Test
  public void testTypeExtraction() {
    Command command = new DefaultCommand() {

      @Option(longName = "list", shortName = "l")
      public void setFoo(List<String> list) {
      }

      @Option(longName = "set", shortName = "s")
      public void setFoo(Set<Character> set) {
      }

      @Option(longName = "collection", shortName = "c")
      public void setFoo(Collection<Integer> collection) {
      }

      @Option(longName = "tree", shortName = "t")
      public void setFoo(TreeSet<String> list) {
      }

      @Option(longName = "al", shortName = "al")
      public void setFoo(ArrayList<String> list) {
      }

      @Option(longName = "array", shortName = "a")
      public void setFoo(int[] list) {
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public void run() throws CommandLineException {
      }
    };

    CommandLine commandLine = new CommandLine();
    CommandManager.define(command, commandLine);

    assertThat(commandLine.getOptions()).hasSize(8);
    OptionModel model = find(commandLine.getOptions(), "list");
    assertThat(model.getType()).isEqualTo(String.class);
    assertThat(model.acceptMultipleValues()).isTrue();

    model = find(commandLine.getOptions(), "set");
    assertThat(model.getType()).isEqualTo(Character.class);
    assertThat(model.acceptMultipleValues()).isTrue();

    model = find(commandLine.getOptions(), "collection");
    assertThat(model.getType()).isEqualTo(Integer.class);
    assertThat(model.acceptMultipleValues()).isTrue();

    model = find(commandLine.getOptions(), "tree");
    assertThat(model.getType()).isEqualTo(String.class);
    assertThat(model.acceptMultipleValues()).isTrue();

    model = find(commandLine.getOptions(), "al");
    assertThat(model.getType()).isEqualTo(String.class);
    assertThat(model.acceptMultipleValues()).isTrue();

    model = find(commandLine.getOptions(), "array");
    assertThat(model.getType()).isEqualTo(Integer.TYPE);
    assertThat(model.acceptMultipleValues()).isTrue();
  }

  @Test
  public void testInjectionOfString() throws CommandLineException {
    HelloCommand command = new HelloCommand();
    CommandLine commandLine = new CommandLine();
    CommandManager.define(command, commandLine);

    assertThat(commandLine.getOptions()).hasSize(3);

    CommandLineParser parser = new CommandLineParser();
    parser.parse(commandLine, "--name", "vert.x");
    CommandManager.inject(command, commandLine);
    command.run();
    assertThat(command.name).isEqualToIgnoringCase("vert.x");
  }

  @Test
  public void testSingleValueInjection() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    CommandWithSingleValue command = new CommandWithSingleValue();
    CommandLine commandLine = new CommandLine();
    CommandManager.define(command, commandLine);

    parser.parse(commandLine, "--boolean", "--short=1", "--byte=1", "--int=1", "--long=1",
        "--double=1.1", "--float=1.1", "--char=c", "--string=hello");
    CommandManager.inject(command, commandLine);

    assertThat(command.aBoolean).isTrue();
    assertThat(command.aShort).isEqualTo((short) 1);
    assertThat(command.aByte).isEqualTo((byte) 1);
    assertThat(command.anInt).isEqualTo(1);
    assertThat(command.aLong).isEqualTo(1l);
    assertThat(command.aDouble).isEqualTo(1.1d);
    assertThat(command.aFloat).isEqualTo(1.1f);
    assertThat(command.aChar).isEqualTo('c');
    assertThat(command.aString).isEqualTo("hello");

    parser.parse(commandLine, "--boolean2", "--short2=1", "--byte2=1", "--int2=1", "--long2=1",
        "--double2=1.1", "--float2=1.1", "--char2=c", "--string=hello");
    CommandManager.inject(command, commandLine);

    assertThat(command.anotherBoolean).isTrue();
    assertThat(command.anotherShort).isEqualTo((short) 1);
    assertThat(command.anotherByte).isEqualTo((byte) 1);
    assertThat(command.anotherInt).isEqualTo(1);
    assertThat(command.anotherLong).isEqualTo(1l);
    assertThat(command.anotherDouble).isEqualTo(1.1d);
    assertThat(command.anotherFloat).isEqualTo(1.1f);
    assertThat(command.anotherChar).isEqualTo('c');
    assertThat(command.aString).isEqualTo("hello");

    parser.parse(commandLine, "--state=NEW");
    CommandManager.inject(command, commandLine);
    assertThat(command.aState).isEqualTo(Thread.State.NEW);

    parser.parse(commandLine, "--person=vert.x");
    CommandManager.inject(command, commandLine);
    assertThat(command.aPerson.name).isEqualTo("vert.x");

    parser.parse(commandLine, "--person2=vert.x");
    CommandManager.inject(command, commandLine);
    assertThat(command.anotherPerson.name).isEqualTo("vert.x");

    parser.parse(commandLine, "--person3=vert.x");
    CommandManager.inject(command, commandLine);
    assertThat(command.aThirdPerson.name).isEqualTo("vert.x");

    parser.parse(commandLine, "--person4=bob,morane");
    CommandManager.inject(command, commandLine);
    assertThat(command.aFourthPerson.first).isEqualTo("bob");
    assertThat(command.aFourthPerson.last).isEqualTo("morane");
  }

  @Test
  public void testMultiValuesInjection() throws CommandLineException {
    CommandLineParser parser = new CommandLineParser();

    CommandWithMultipleValues command = new CommandWithMultipleValues();
    CommandLine commandLine = new CommandLine();
    CommandManager.define(command, commandLine);

    parser.parse(commandLine, "--persons=x", "--persons", "y", "z");
    CommandManager.inject(command, commandLine);
    assertThat(command.persons).hasSize(3);

    parser.parse(commandLine, "--persons2=x", "--persons2", "y", "z");
    CommandManager.inject(command, commandLine);
    assertThat(command.persons2).hasSize(3);

    parser.parse(commandLine, "--persons3=x", "--persons3", "y", "z");
    CommandManager.inject(command, commandLine);
    assertThat(command.persons3).hasSize(3);

    parser.parse(commandLine, "--persons4=x:y:z");
    CommandManager.inject(command, commandLine);
    assertThat(command.persons4).hasSize(3);

    parser.parse(commandLine, "--states=NEW", "--states", "BLOCKED", "RUNNABLE");
    CommandManager.inject(command, commandLine);
    assertThat(command.states).hasSize(3).containsExactly(Thread.State.NEW, Thread.State.BLOCKED,
        Thread.State.RUNNABLE);

    parser.parse(commandLine, "--ints=1", "--ints", "2", "3");
    CommandManager.inject(command, commandLine);
    assertThat(command.ints).hasSize(3).containsExactly(1, 2, 3);

    parser.parse(commandLine, "--shorts=1", "--shorts", "2", "3");
    CommandManager.inject(command, commandLine);
    assertThat(command.shorts).hasSize(3).containsExactly((short) 1, (short) 2, (short) 3);

    parser.parse(commandLine, "--strings=a");
    CommandManager.inject(command, commandLine);
    assertThat(command.strings).hasSize(1).containsExactly("a");

    parser.parse(commandLine, "--doubles=1", "--doubles", "2.2", "3.3");
    CommandManager.inject(command, commandLine);
    assertThat(command.doubles).hasSize(3).containsExactly(1.0, 2.2, 3.3);
  }

  @Test
  public void testArgumentInjection() throws CommandLineException {

    AtomicReference<String> reference = new AtomicReference<>();

    Command command = new DefaultCommand() {

      @Argument(index = 0)
      public void setX(String s) {
        reference.set(s);
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public void run() throws CommandLineException {
        // Do nothing.
      }
    };

    CommandLine line = new CommandLine();
    CommandManager.define(command, line);
    line.parse("foo");
    CommandManager.inject(command, line);
    assertThat(reference.get()).isEqualTo("foo");
  }

  @Test
  public void testArgumentInjectionWithConvertedByAndDefaultValue() throws CommandLineException {

    AtomicReference<Person4> reference = new AtomicReference<>();

    Command command = new DefaultCommand() {

      @Argument(index = 0)
      @DefaultValue("Bill,Balantine")
      @ConvertedBy(Person4Converter.class)
      public void setX(Person4 s) {
        reference.set(s);
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public void run() throws CommandLineException {
        // Do nothing.
      }
    };

    CommandLine line = new CommandLine();
    CommandManager.define(command, line);
    line.parse("Bob,Morane");
    CommandManager.inject(command, line);
    assertThat(reference.get().first).isEqualTo("Bob");
    assertThat(reference.get().last).isEqualTo("Morane");

    line.parse();
    CommandManager.inject(command, line);
    assertThat(reference.get().first).isEqualTo("Bill");
    assertThat(reference.get().last).isEqualTo("Balantine");
  }

  @Test
  public void testArgumentInjectionWithSeveralArguments() throws CommandLineException {

    AtomicReference<String> x = new AtomicReference<>();
    AtomicReference<Integer> y = new AtomicReference<>();

    Command command = new DefaultCommand() {

      @Argument(index = 0)
      public void setX(String s) {
        x.set(s);
      }

      @Argument(index = 1)
      public void setY(int s) {
        y.set(s);
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public void run() throws CommandLineException {
        // Do nothing.
      }
    };

    CommandLine line = new CommandLine();
    CommandManager.define(command, line);
    line.parse("foo", "1");
    CommandManager.inject(command, line);
    assertThat(x.get()).isEqualTo("foo");
    assertThat(y.get()).isEqualTo(1);
  }

  @Test
  public void testArgumentWithDefaultValue() throws CommandLineException {

    AtomicReference<String> x = new AtomicReference<>();
    AtomicReference<Integer> y = new AtomicReference<>();

    Command command = new DefaultCommand() {

      @Argument(index = 0)
      public void setX(String s) {
        x.set(s);
      }

      @Argument(index = 1)
      @DefaultValue("25")
      public void setY(int s) {
        y.set(s);
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public void run() throws CommandLineException {
        // Do nothing.
      }
    };

    CommandLine line = new CommandLine();
    CommandManager.define(command, line);
    line.parse("foo");
    CommandManager.inject(command, line);
    assertThat(x.get()).isEqualTo("foo");
    assertThat(y.get()).isEqualTo(25);
  }

  private OptionModel find(List<OptionModel> options, String name) {
    final List<OptionModel> match = options.stream().filter(c -> c.getLongName().equalsIgnoreCase(name))
        .collect(Collectors.toList());
    if (match.isEmpty()) {
      fail("Cannot find option '" + name + "' in " + options.stream().map(OptionModel::getLongName)
          .collect(Collectors.toList()));
    }
    return match.get(0);
  }

  private class CommandWithSingleValue extends DefaultCommand {

    String aString;
    Thread.State aState;

    boolean aBoolean;
    Boolean anotherBoolean;

    byte aByte;
    Byte anotherByte;

    char aChar;
    Character anotherChar;

    double aDouble;
    Double anotherDouble;

    float aFloat;
    Float anotherFloat;

    int anInt;
    Integer anotherInt;

    long aLong;
    Long anotherLong;

    short aShort;
    Short anotherShort;

    Person aPerson;
    Person2 anotherPerson;
    Person3 aThirdPerson;
    Person4 aFourthPerson;

    @Option(longName = "boolean", shortName = "Z")
    public void setaBoolean(boolean aBoolean) {
      this.aBoolean = aBoolean;
    }

    @Option(longName = "byte", shortName = "B")
    public void setaByte(byte aByte) {
      this.aByte = aByte;
    }

    @Option(longName = "char", shortName = "C")
    public void setaChar(char aChar) {
      this.aChar = aChar;
    }

    @Option(longName = "double", shortName = "D")
    public void setaDouble(double aDouble) {
      this.aDouble = aDouble;
    }

    @Option(longName = "float", shortName = "F")
    public void setaFloat(float aFloat) {
      this.aFloat = aFloat;
    }

    @Option(longName = "long", shortName = "J")
    public void setaLong(long aLong) {
      this.aLong = aLong;
    }

    @Option(longName = "int", shortName = "I")
    public void setAnInt(int anInt) {
      this.anInt = anInt;
    }

    @Option(longName = "boolean2", shortName = "AZ")
    public void setAnotherBoolean(Boolean anotherBoolean) {
      this.anotherBoolean = anotherBoolean;
    }

    @Option(longName = "byte2", shortName = "AB")
    public void setAnotherByte(Byte anotherByte) {
      this.anotherByte = anotherByte;
    }

    @Option(longName = "char2", shortName = "AC")
    public void setAnotherChar(Character anotherChar) {
      this.anotherChar = anotherChar;
    }

    @Option(longName = "double2", shortName = "AD")
    public void setAnotherDouble(Double anotherDouble) {
      this.anotherDouble = anotherDouble;
    }

    @Option(longName = "float2", shortName = "AF")
    public void setAnotherFloat(Float anotherFloat) {
      this.anotherFloat = anotherFloat;
    }

    @Option(longName = "int2", shortName = "AI")
    public void setAnotherInt(Integer anotherInt) {
      this.anotherInt = anotherInt;
    }

    @Option(longName = "long2", shortName = "AJ")
    public void setAnotherLong(Long anotherLong) {
      this.anotherLong = anotherLong;
    }

    @Option(longName = "person2", shortName = "p2")
    public void setAnotherPerson(Person2 anotherPerson) {
      this.anotherPerson = anotherPerson;
    }

    @Option(longName = "short2", shortName = "AS")
    public void setAnotherShort(Short anotherShort) {
      this.anotherShort = anotherShort;
    }

    @Option(longName = "person", shortName = "p")
    public void setaPerson(Person aPerson) {
      this.aPerson = aPerson;
    }

    @Option(longName = "person4", shortName = "p4")
    @ConvertedBy(Person4Converter.class)
    public void setAFourthPerson(Person4 aPerson) {
      this.aFourthPerson = aPerson;
    }

    @Option(longName = "short", shortName = "s")
    public void setaShort(short aShort) {
      this.aShort = aShort;
    }

    @Option(longName = "state", shortName = "st")
    public void setaState(Thread.State aState) {
      this.aState = aState;
    }

    @Option(longName = "string", shortName = "str")
    public void setaString(String aString) {
      this.aString = aString;
    }

    @Option(longName = "person3", shortName = "p3")
    public void setaThirdPerson(Person3 aThirdPerson) {
      this.aThirdPerson = aThirdPerson;
    }

    @Override
    public String name() {
      return "single";
    }


    @Override
    public void run() throws CommandLineException {
      // Do nothing.
    }
  }


  private class CommandWithMultipleValues extends DefaultCommand {

    List<Person> persons;
    List<Person> persons2;
    List<Person> persons3;
    Set<Thread.State> states;

    Collection<Integer> ints;
    Set<String> strings;
    List<Short> shorts;
    double[] doubles;

    List<Person> persons4;

    @Option(longName = "doubles", shortName = "ds")
    public void setDoubles(double[] doubles) {
      this.doubles = doubles;
    }

    @Option(longName = "ints", shortName = "is")
    public void setInts(Collection<Integer> ints) {
      this.ints = ints;
    }

    @Option(longName = "persons2", shortName = "ps2")
    public void setPersons2(List<Person> persons2) {
      this.persons2 = persons2;
    }

    @Option(longName = "persons3", shortName = "ps3")
    public void setPersons3(List<Person> persons3) {
      this.persons3 = persons3;
    }

    @Option(longName = "persons4", shortName = "ps4")
    @ParsedAsList(separator = ":")
    public void setPersons4(List<Person> persons4) {
      this.persons4 = persons4;
    }

    @Option(longName = "persons", shortName = "ps")
    public void setPersons(List<Person> persons) {
      this.persons = persons;
    }

    @Option(longName = "shorts", shortName = "ss")
    public void setShorts(List<Short> shorts) {
      this.shorts = shorts;
    }

    @Option(longName = "states", shortName = "sts")
    public void setStates(Set<Thread.State> states) {
      this.states = states;
    }

    @Option(longName = "strings", shortName = "str")
    public void setStrings(Set<String> strings) {
      this.strings = strings;
    }

    @Override
    public String name() {
      return "multi";
    }


    @Override
    public void run() throws CommandLineException {
      // Do nothing.
    }
  }


}