package io.vertx.core.cli;

/**
 * A plug-in to the Vert.x command or {@link io.vertx.core.Starter} class.
 * <p/>
 * <h3>Conventions</h3>
 * <p>As well as implementing this interface a command must adhere to a number of
 * conventions:</p>
 * <ul>
 * <li>The class must have a public no argument constructor</li>
 * <li>The class must be concrete</li>
 * </ol>
 * <p/>
 * {@link Command} implementation can retrieve argument and option using the {@link Argument} and {@link Option}
 * annotations. Documentation / help is provided using the {@link Summary} (single sentence) and {@link Description}
 * annotations.
 * <p>
 * Commands follow a strict lifecycle. First, commands are initialized ({@link #initialize(ExecutionContext)}). At
 * this stage, commands can update the command line model, but values are still not set. Then, the user command line
 * is parsed and the values set / injected. Once this is done, the {@link #setup()} method is called. It lets you
 * validate the inputs and prepare the environment is needed. The {@link #run()} method is called immediately after
 * {@link #setup()}, and executes the command. Finally, once the command has completed, the {@link #tearDown()}
 * method is called. In this method you have the opportunity to cleanup.
 * </p>
 */
public interface Command {

  /**
   * @return the command name such as 'run'.
   */
  String name();

  /**
   * Initializes the command.
   * The command line model has been retrieved, but can still be updated. Values are not set / injected yet.
   *
   * @param ec the execution context
   * @throws CommandLineException If anything went wrong.
   */
  void initialize(ExecutionContext ec) throws CommandLineException;

  /**
   * Set up the command execution environment.
   * The command line model has been retrieved and is frozen. Values has been set / injected. You can use this callback
   * to validate the inputs.
   *
   * @throws CommandLineException if the validation failed
   */
  void setup() throws CommandLineException;

  /**
   * Executes the command.
   *
   * @throws CommandLineException If anything went wrong.
   */
  void run() throws CommandLineException;

  /**
   * The command has been executed. Use this method to cleanup the environment.
   *
   * @throws CommandLineException if anything went wrong
   */
  void tearDown() throws CommandLineException;

}
