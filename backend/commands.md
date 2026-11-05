# Java & Docker Terminal Command Reference

---

### 1.  Install Nodemon Globally
> **Usage:** Run once globally across your system | **Function:** Installs nodemon CLI tool via npm.
```bash
npm install -g nodemon

2. Live-Reload Java with Nodemon
Usage: During development for auto-restart | Function: Watches src for .java edits and triggers Maven compilation.
nodemon --watch src --ext java --exec "mvn compile exec:java"

3. Compile and Run via Maven
Usage: Standard build and run step | Function: Compiles Java sources and runs configured main class.

Bash
mvn compile exec:java


4. Run Specific Main Class
Usage: Projects with multiple main entry points | Function: Executes targeted main class via fully qualified package path.

Bash
mvn exec:java -Dexec.mainClass="com.typeme.Main"

5. Check Maven Version
Usage: Verify installation and versions | Function: Outputs active Maven version and JDK info.

Bash
mvn --version


6. Spin Up Docker Compose Services
Usage: Start background infrastructure | Function: Creates and runs containers in detached mode (-d).

Bash
docker compose up -d


7. Start Stopped Container
Usage: Boot existing turned-off container | Function: Starts target container by name without rebuilding.

Bash
docker start typeme_db

8. List All Docker Containers
Usage: Inspect active and inactive containers | Function: Displays status list of all local containers (-a).

Bash
docker ps -a



9. Tear Down Containers & Volumes
Usage: Wipe databases and reset state | Function: Stops containers and permanently removes attached volumes (-v).

Bash
docker compose down -v
Set Temporary Java Home (CMD)

Usage: Single terminal session JDK quick-fix | Function: Sets JAVA_HOME variable for current terminal instance.

10. DOS
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot
Fetch User PATH (PowerShell)
Usage: Read current system PATH variable | Function: Queries Windows User environment PATH registry entry.

11. PowerShell
$userPath = [Environment]::GetEnvironmentVariable("PATH", "User")
Permanent Maven Path Setup (PowerShell)
Usage: Automation scripts for environment setup | Function: Appends Maven binary directory to User PATH if missing.

12. PowerShell
if ($userPath -notlike "*$mavenBin*") {                
    [Environment]::SetEnvironmentVariable("PATH", "$userPath;$mavenBin", "User")
    Write-Host "Maven added to PATH successfully!"
} else {
    Write-Host "Maven is already in your PATH!"
}