@echo off
cd /d "%~dp0"
title Smart Canteen
echo ===== Smart Canteen =====
set "DBUSER="
set "DBPASS="
set /p DBUSER=MySQL username (press Enter for root): 
if not defined DBUSER set "DBUSER=root"
set /p DBPASS=MySQL password: 
set "MYSQL_PWD=%DBPASS%"
set "SPRING_DATASOURCE_USERNAME=%DBUSER%"
set "SPRING_DATASOURCE_PASSWORD=%DBPASS%"

set "MYSQL_EXE="
where mysql >nul 2>nul && set "MYSQL_EXE=mysql"
if not defined MYSQL_EXE for /d %%D in ("%ProgramFiles%\MySQL\MySQL Server*") do if exist "%%D\bin\mysql.exe" set "MYSQL_EXE=%%D\bin\mysql.exe"
if not defined MYSQL_EXE goto nomysql

"%MYSQL_EXE%" -u "%DBUSER%" -e "USE canteen_db" >nul 2>nul
if not errorlevel 1 goto rundb
echo Creating database canteen_db ...
"%MYSQL_EXE%" -u "%DBUSER%" --default-character-set=utf8mb4 < "database\schema.sql"
if errorlevel 1 goto dberror
echo Database created.
goto rundb

:nomysql
echo Could not find the mysql program, so the database was not checked.
echo Make sure canteen_db exists: run database\schema.sql in MySQL Workbench once.
goto rundb

:dberror
echo.
echo Could not create the database. Check your MySQL username and password and that MySQL is running.
pause
exit /b 1

:rundb
echo.
echo Starting Smart Canteen. The browser opens by itself when it is ready.
echo First run downloads libraries and takes a few minutes. Close this window to stop the app.
echo.
call mvnw.cmd spring-boot:run
echo.
echo The app stopped. Scroll up to see any error.
pause
