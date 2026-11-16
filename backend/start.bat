@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not defined GOOGLE_CLIENT_ID (
    echo [Log] Set GOOGLE_CLIENT_ID in the environment before starting.
    exit /b 1
)
if not defined GOOGLE_CLIENT_SECRET (
    echo [Log] Set GOOGLE_CLIENT_SECRET in the environment before starting.
    exit /b 1
)
if not defined DATABASE_PASSWORD (
    echo [Log] Set DATABASE_PASSWORD in the environment before starting.
    exit /b 1
)

echo [Log] Starting Backend Server via Maven...
mvn compile exec:j