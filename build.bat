@echo off
REM Installs the BOM and the parent, then builds the reactor.
REM The parent imports the BOM. Maven does not resolve that import from a
REM sibling module, so a clean machine needs these two installs first.
REM
REM   build.bat
REM   build.bat -DskipTests
setlocal
pushd "%~dp0"
set MVN=call "%~dp0mvnw.cmd" -B

echo [1/3] BOM and parent
%MVN% -q -f ff-base-bom\pom.xml install || goto failed
%MVN% -q -f ff-base-parent\pom.xml install || goto failed

echo [2/3] Reactor
%MVN% install %* || goto failed

echo [3/3] Done.
popd
endlocal
exit /b 0

:failed
echo.
echo BUILD FAILED. See the Maven output above.
popd
endlocal
exit /b 1
