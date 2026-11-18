@echo off
setlocal
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    call "%MAVEN_HOME%\bin\mvn.cmd" %*
    goto done
)
for /f "delims=" %%M in ('where.exe mvn.cmd 2^>nul') do (
    if /i not "%%~fM"=="%~f0" (
        call "%%~fM" %*
        goto done
    )
)
if exist "%LOCALAPPDATA%\Programs\apache-maven-3.9.9\bin\mvn.cmd" (
    call "%LOCALAPPDATA%\Programs\apache-maven-3.9.9\bin\mvn.cmd" %*
    goto done
)
echo Maven was not found. Install Maven 3.9+ and set MAVEN_HOME or add its bin folder to PATH. 1>&2
exit /b 1

:done
exit /b %errorlevel%
