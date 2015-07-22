package io.vertx.core.cli;

import io.vertx.core.cli.converters.Person4;
import io.vertx.core.cli.converters.Person4Converter;
import org.junit.Test;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class ArgumentModelTest {


  @Test(expected = CommandLineException.class)
  public void testThatArgumentWithTheSameIndexAreDetected() throws CommandLineException {
    CommandLine line = new CommandLine();
    line.addArgument(ArgumentModel.<String>builder().index(0).type(String.class).build());
    line.addArgument(ArgumentModel.<String>builder().index(0).type(String.class).build());
    line.parse("a", "b");
  }

  @Test
  public void testThatMissingArgumentsAreDetected() throws CommandLineException {
    CommandLine line = new CommandLine();
    line.addArgument(ArgumentModel.<String>builder().index(0).type(String.class).required().build());
    line.addArgument(ArgumentModel.<String>builder().index(1).type(String.class).required().build());

    try {
      line.parse();
      fail("Missing Value Exception expected");
    } catch (MissingValueException e) {
      // OK
    }

    try {
      line.parse("a");
      fail("Missing Value Exception expected");
    } catch (MissingValueException e) {
      // OK
    }

    line.parse("a", "b");
  }

  @Test
  public void testMixedOfRequiredAnOptionalArguments() throws CommandLineException {
    CommandLine line = new CommandLine();
    line.addArgument(ArgumentModel.<String>builder().index(0).type(String.class).required().build());
    line.addArgument(ArgumentModel.<String>builder().index(1).type(String.class).required(false).build());

    try {
      line.parse();
      fail("Missing Value Exception expected");
    } catch (MissingValueException e) {
      // OK
    }

    line.parse("a");
    assertThat((String) line.getArgumentValue(0)).isEqualTo("a");
    assertThat((String) line.getArgumentValue(1)).isNull();

    line.parse("a", "b");
    assertThat((String) line.getArgumentValue(0)).isEqualTo("a");
    assertThat((String) line.getArgumentValue(1)).isEqualTo("b");
  }

  @Test
  public void testThatArgumentsAreOrdered() throws CommandLineException {
    CommandLine line = new CommandLine();
    line.addArgument(ArgumentModel.<String>builder().index(1).argName("1").type(String.class).build());
    line.addArgument(ArgumentModel.<String>builder().index(0).argName("2").type(String.class).build());
    line.addArgument(ArgumentModel.<String>builder().index(2).argName("3").type(String.class).build());

    assertThat(line.getArguments()).hasSize(3);
    Iterator<ArgumentModel> iterator = line.getArguments().iterator();
    assertThat(iterator.next().getArgName()).isEqualTo("2");
    assertThat(iterator.next().getArgName()).isEqualTo("1");
    assertThat(iterator.next().getArgName()).isEqualTo("3");
    line.parse("a", "b", "c");

    iterator = line.getArguments().iterator();
    assertThat((String) iterator.next().getValue()).isEqualTo("a");
    assertThat((String) line.getArgumentValue("2")).isEqualTo("a");
    assertThat((String) iterator.next().getValue()).isEqualTo("b");
    assertThat((String) line.getArgumentValue("1")).isEqualTo("b");
    assertThat((String) iterator.next().getValue()).isEqualTo("c");
    assertThat((String) line.getArgumentValue("3")).isEqualTo("c");
  }

  @Test
  public void testThatDefaultValuesAreHandled() throws CommandLineException {
    CommandLine line = new CommandLine();
    line.addArgument(ArgumentModel.<String>builder().index(0).argName("1").type(String.class)
        .defaultValue("hello").build());

    line.parse("a");
    assertThat((String) line.getArgumentValue(0)).isEqualTo("a");
    line.parse();
    assertThat((String) line.getArgumentValue(0)).isEqualTo("hello");
  }

  @Test
  public void testThatInvalidValuesAreReported() throws CommandLineException {
    CommandLine line = new CommandLine();
    line.addArgument(ArgumentModel.<Integer>builder().index(0).argName("1").type(Integer.class).build());

    try {
      line.parse("a");
      fail("Exception expected");
    } catch (InvalidValueException e) {
      assertThat(e.getArgument().getIndex()).isEqualTo(0);
      assertThat(e.getArgument().getArgName()).isEqualTo("1");
      assertThat(e.getValue()).isEqualTo("a");
    }

  }

  @Test
  public void testThatInvalidValuesAsDefaultValueAreReported() throws CommandLineException {
    CommandLine line = new CommandLine();
    try {
      line.addArgument(ArgumentModel.<Integer>builder().index(0).argName("1").type(Integer.class).defaultValue("a").build());
    } catch (IllegalArgumentException e) {
      assertThat(e.getCause()).isInstanceOf(InvalidValueException.class);
      InvalidValueException cause = (InvalidValueException) e.getCause();
      assertThat(cause.getArgument().getIndex()).isEqualTo(0);
      assertThat(cause.getArgument().getArgName()).isEqualTo("1");
      assertThat(cause.getValue()).isEqualTo("a");
    }
  }

  @Test
  public void testThatConvertersAreHandled() throws CommandLineException {
    CommandLine line = new CommandLine();
    final ArgumentModel<Person4> arg = ArgumentModel.<Person4>builder().index(0).argName("person")
        .type(Person4.class)
        .convertedBy(Person4Converter.class)
        .defaultValue("Bill,Ballantine").build();
    line.addArgument(arg);

    line.parse("Bob,Morane");
    Person4 person = arg.getValue();
    assertThat(person.first).isEqualTo("Bob");
    assertThat(person.last).isEqualTo("Morane");


    line.parse();
    person = arg.getValue();
    assertThat(person.first).isEqualTo("Bill");
    assertThat(person.last).isEqualTo("Ballantine");
  }

}