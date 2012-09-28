# Building Vert.x

The build uses Gradle. The build script will automatically install the bits it needs if they aren't already installed.

### Prerequisites
* jython - for building python support (even if you don't plan to use Jython)
 * Ensure that the `JYTHON_HOME` environment variable points to the Jython base directory, or you'll get errors like `A problem occurred starting process 'command 'null/jython''`.
* yard - for building ruby docs (you can exclude it passing `-x yard` to the build tool)

## On *nix

Use `./mk`

`.mk tasks` to show the list of tasks

## On Windows

Use `wmk.bat`

`wmk.bat tasks` to show list of tasks
