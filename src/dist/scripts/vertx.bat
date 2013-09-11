@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  vertx startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@rem Add default JVM options here. You can also use JAVA_OPTS and VERTX_OPTS to pass JVM options to this script.
set JVM_OPTS=

set JMX_OPTS=
@rem To enable JMX uncomment the following
@rem set JMX_OPTS=-Dcom.sun.management.jmxremote -Dvertx.management.jmx=true -Dhazelcast.jmx=true

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set VERTX_HOME=%DIRNAME%..

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Add module option to commandline, if VERTX_MODS was set
if not "%VERTX_MODS%" == "" set VERTX_MODULE_OPTS="-Dvertx.mods=%VERTX_MODS%"

@rem Configure JUL using custom properties file
if "%VERTX_JUL_CONFIG%" == "" set VERTX_JUL_CONFIG=%VERTX_HOME%\conf\logging.properties

@rem Get command-line arguments, handling Windowz variants

if not "%OS%" == "Windows_NT" goto win9xME_args
if "%@eval[2+2]" == "4" goto 4NT_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*
goto execute

:4NT_args
@rem Get arguments from the 4NT Shell from JP Software
set CMD_LINE_ARGS=%$

:execute
@rem Setup the command line

set CLASSPATH=%CLASSPATH%;%VERTX_HOME%\conf;%VERTX_HOME%\lib\*

@rem Execute vertx
"%JAVA_EXE%" %JVM_OPTS% %JMX_OPTS% %JAVA_OPTS% %VERTX_OPTS% %VERTX_MODULE_OPTS% -Djava.util.logging.config.file="%VERTX_JUL_CONFIG%" -Dvertx.home="%VERTX_HOME%" -classpath "%CLASSPATH%" org.vertx.java.platform.impl.cli.Starter %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable VERTX_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%VERTX_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
