@echo off

rem This script lives on the vert.x bin directory which should be on the users PATH.
rem Make sure a Java 7 bin-dir is on your PATH

setlocal enabledelayedexpansion

for %%? in ("%~dp0..") do set VERTX_HOME=%%~f?
set VERTX_CP=%VERTX_HOME%\conf;
for %%a in ("%VERTX_HOME%\lib\jars\*.jar") do set VERTX_CP=!VERTX_CP!%%a;
for /d %%a in ("%VERTX_HOME%\lib\*") do set VERTX_CP=!VERTX_CP!%%a;

java -Djava.util.logging.config.file=%VERTX_HOME%\conf\logging.properties -Djruby.home=%JRUBY_HOME% -cp %VERTX_CP% org.vertx.java.core.deploy.impl.cli.VertxMgr %*
