@echo off
setlocal
set "MVN_VERSION=3.9.9"
set "MVN_DIR=%~dp0.mvn"
set "MVN_HOME=%MVN_DIR%\apache-maven-%MVN_VERSION%"
if exist "%MVN_HOME%\bin\mvn.cmd" goto run
echo Downloading Maven %MVN_VERSION% (one time only, needs internet)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VERSION%/apache-maven-%MVN_VERSION%-bin.zip' -OutFile '%MVN_DIR%\maven.zip'; Expand-Archive -Path '%MVN_DIR%\maven.zip' -DestinationPath '%MVN_DIR%' -Force; Remove-Item '%MVN_DIR%\maven.zip'"
if errorlevel 1 (
  echo Maven download failed. Check your internet, or install Maven and run: mvn spring-boot:run
  exit /b 1
)
:run
call "%MVN_HOME%\bin\mvn.cmd" %*
endlocal
