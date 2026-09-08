@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1

REM ============================================================
REM  OculiX Portable Manager (Windows)
REM  ------------------------------------------------------------
REM  A single .bat that manages a fully-portable OculiX install
REM  living under %USERPROFILE%\.oculix\
REM
REM    - Portable Temurin JRE 21 (no admin required)
REM    - OculiX IDE + MCP server (release of your choice)
REM    - Cohabitation-safe: never deletes files placed there by
REM      other tools (e.g. Operix Python bridge in lib\)
REM
REM  Requires Windows 10 1803+ (curl, tar, powershell — bundled
REM  since 2018).
REM ============================================================

REM --- ANSI colour setup (via PowerShell for reliability across shells) ---
for /F "delims=" %%a in ('powershell -NoProfile -Command "[char]27"') do set "ESC=%%a"
set "C_RESET=%ESC%[0m"
set "C_BOLD=%ESC%[1m"
set "C_CYAN=%ESC%[1;36m"
set "C_GREEN=%ESC%[1;32m"
set "C_YELLOW=%ESC%[1;33m"
set "C_RED=%ESC%[1;31m"
set "C_MAGENTA=%ESC%[1;35m"
set "C_DIM=%ESC%[2m"

REM --- Constants ---
set "BASE=%USERPROFILE%\.oculix"
set "IDE_JAR=%BASE%\oculixide.jar"
set "MCP_JAR=%BASE%\oculix-mcp-server.jar"
set "JAVAW=%BASE%\jre\bin\javaw.exe"
set "JAVA=%BASE%\jre\bin\java.exe"
set "ICON=%BASE%\oculix.ico"
set "INFO=%BASE%\install.info"

set "JRE_URL=https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"
set "ICON_URL=https://raw.githubusercontent.com/oculix-org/Oculix/master/API/src/main/resources/htdocs/favicon.ico"
set "GH_API=https://api.github.com/repos/oculix-org/Oculix/releases"

REM --- Menu loop ---
:menu
cls
call :read_status
echo ==================================================
echo          %C_BOLD%🦎  OculiX Portable Manager%C_RESET%
echo ==================================================
echo Status: %STATUS_TXT%
echo Base:   %BASE%
echo ==================================================
echo.
echo RUN
echo   1 - Launch OculiX IDE
echo   2 - Launch OculiX MCP Server (stdio)
echo   3 - Launch OculiX MCP Server (HTTP :7337)
echo.
echo DISCOVER
echo   4 - Show MCP help ^& commands
echo   T - Show MCP tools (live query via JSON-RPC)
echo   5 - Show installed versions ^& disk usage
echo.
echo MAINTAIN
echo   6 - Install / Update to another version
echo   7 - Rotate MCP audit signing key
echo   8 - Verify MCP audit chain
echo.
echo ADVANCED
echo   9 - Open install folder in Explorer
echo   0 - Uninstall (installer artefacts only)
echo   ? - Help ^& configuration (what this manager does)
echo   Q - Quit
echo ==================================================
set /p "PICK=Your choice: "
if /I "!PICK!"=="1" goto :launch_ide
if /I "!PICK!"=="2" goto :launch_mcp_stdio
if /I "!PICK!"=="3" goto :launch_mcp_http
if /I "!PICK!"=="4" goto :show_mcp_help
if /I "!PICK!"=="T" goto :show_mcp_tools
if /I "!PICK!"=="5" goto :show_status
if /I "!PICK!"=="6" goto :install
if /I "!PICK!"=="7" goto :rotate_key
if /I "!PICK!"=="8" goto :verify_chain
if /I "!PICK!"=="9" goto :open_folder
if /I "!PICK!"=="0" goto :uninstall
if /I "!PICK!"=="?" goto :show_help
if /I "!PICK!"=="Q" goto :quit
echo   %C_RED%[!]%C_RESET% Invalid choice.
timeout /t 1 >nul
goto :menu


REM ============================================================
REM  Helpers
REM ============================================================

:read_status
set "STATUS_TXT=not installed"
if not exist "%INFO%" exit /b
for /F "usebackq tokens=1,2 delims==" %%A in ("%INFO%") do (
  if /I "%%A"=="tag" set "INST_TAG=%%B"
  if /I "%%A"=="date" set "INST_DATE=%%B"
)
set "STATUS_TXT=installed !INST_TAG! (!INST_DATE!)"
exit /b

:check_installed
set "INSTALLED=0"
if exist "%IDE_JAR%" if exist "%JAVAW%" set "INSTALLED=1"
exit /b

:need_install
call :check_installed
if "%INSTALLED%"=="0" (
  echo.
  echo   %C_RED%[!]%C_RESET% OculiX is not installed yet. Run [6] first.
  echo.
  pause
  goto :menu
)
exit /b

:pause_and_menu
echo.
pause
goto :menu


REM ============================================================
REM  Actions
REM ============================================================

:launch_ide
call :need_install
echo   %C_CYAN%[launch]%C_RESET% OculiX IDE...
start "" "%JAVAW%" -jar "%IDE_JAR%"
timeout /t 2 >nul
goto :menu

:launch_mcp_stdio
call :need_install
echo.
echo ==================================================
echo MCP Server on STDIO
echo ==================================================
echo Server speaks JSON-RPC 2.0 on stdin/stdout.
echo Pipe requests in, read responses on stdout.
echo See item [?] for cookbook and command examples.
echo Close this window to stop the server.
echo ==================================================
echo.
"%JAVA%" -jar "%MCP_JAR%" run
goto :pause_and_menu

:launch_mcp_http
call :need_install
echo.
echo ==================================================
echo MCP Server on HTTP :7337
echo ==================================================
echo Server URL:  http://127.0.0.1:7337/mcp
echo Loopback only (127.0.0.1). Not reachable from network.
echo POST JSON-RPC requests to that URL.
echo See item [?] for cookbook and command examples.
echo Close this window to stop the server.
echo ==================================================
echo.
"%JAVA%" -jar "%MCP_JAR%" serve --host 127.0.0.1 --port 7337
goto :pause_and_menu

:show_mcp_help
call :need_install
echo.
"%JAVA%" -jar "%MCP_JAR%" --help
goto :pause_and_menu

:show_mcp_tools
call :need_install
echo.
echo Querying MCP server via JSON-RPC (tools/list)...
echo.
set "TOOLS_PS1=%TEMP%\oculix-tools-list.ps1"
call :write_tools_lister
powershell -NoProfile -ExecutionPolicy Bypass -File "%TOOLS_PS1%" "%JAVA%" "%MCP_JAR%"
del "%TOOLS_PS1%" 2>nul
goto :pause_and_menu

:show_status
echo.
echo   %C_BOLD%Install status%C_RESET%
echo   ──────────────
if exist "%INFO%" (type "%INFO%") else (echo   Not installed.)
echo.
echo   %C_BOLD%Disk usage%C_RESET%
echo   ──────────
if not exist "%BASE%" echo   %C_DIM%Nothing yet.%C_RESET% & goto :pause_and_menu
powershell -NoProfile -Command "$s=(Get-ChildItem '%BASE%' -Recurse -File -EA SilentlyContinue | Measure-Object Length -Sum).Sum; '{0:N1} MB total in {1}' -f ($s/1MB), '%BASE%'"
echo.
echo   %C_DIM%Sub-folders:%C_RESET%
for /D %%D in ("%BASE%\*") do powershell -NoProfile -Command "$s=(Get-ChildItem '%%D' -Recurse -File -EA SilentlyContinue | Measure-Object Length -Sum).Sum; '    {0,-30}  {1,8:N1} MB' -f '%%~nxD\', ($s/1MB)"
goto :pause_and_menu

:rotate_key
call :need_install
echo.
"%JAVA%" -jar "%MCP_JAR%" rotate-key
goto :pause_and_menu

:verify_chain
call :need_install
echo.
"%JAVA%" -jar "%MCP_JAR%" verify
goto :pause_and_menu

:open_folder
if not exist "%BASE%" mkdir "%BASE%"
start "" explorer "%BASE%"
timeout /t 1 >nul
goto :menu

:uninstall
call :check_installed
if "%INSTALLED%"=="0" (
  echo.
  echo   %C_YELLOW%[!]%C_RESET% Nothing to uninstall.
  goto :pause_and_menu
)
echo.
echo   %C_YELLOW%This will remove ONLY the artefacts placed by this installer:%C_RESET%
echo     - %BASE%\jre\
echo     - %BASE%\oculixide.jar
echo     - %BASE%\oculix-mcp-server.jar
echo     - %BASE%\oculix.ico
echo     - %BASE%\install.info
echo     - Desktop\OculiX.lnk  +  Desktop\OculiX MCP.lnk
echo.
echo   %C_GREEN%Preserved:%C_RESET% lib\ (Operix bridge), .oculix-mcp\ (audit journal),
echo              and anything else you or another tool has placed
echo              under %BASE%\.
echo.
set /p "CONFIRM=  Type YES to confirm: "
if not "!CONFIRM!"=="YES" (
  echo.
  echo   %C_CYAN%[cancelled]%C_RESET%
  goto :pause_and_menu
)
if exist "%BASE%\jre" rmdir /s /q "%BASE%\jre"
if exist "%IDE_JAR%" del "%IDE_JAR%"
if exist "%MCP_JAR%" del "%MCP_JAR%"
if exist "%ICON%" del "%ICON%"
if exist "%INFO%" del "%INFO%"
for %%F in ("%USERPROFILE%\Desktop\OculiX.lnk" "%USERPROFILE%\Desktop\OculiX MCP.lnk") do (
  if exist %%F del %%F
)
echo.
echo   %C_GREEN%[done]%C_RESET% Installer artefacts removed.
goto :pause_and_menu

:show_help
cls
echo.
echo ==================================================
echo   OculiX Portable Manager - Help
echo ==================================================
echo.
echo WHAT THIS MANAGER DOES
echo ----------------------
echo A single .bat that installs, launches and maintains
echo a fully-portable OculiX under %USERPROFILE%\.oculix\
echo (no admin, no PATH modification, no side effects on
echo your system Java).
echo.
echo FILE LAYOUT ONCE INSTALLED
echo --------------------------
echo   %USERPROFILE%\.oculix\
echo     jre\                    Temurin JRE 21 (portable)
echo     oculixide.jar           OculiX visual scripting IDE
echo     oculix-mcp-server.jar   OculiX MCP server
echo     oculix.ico              Gecko icon for shortcuts
echo     install.info            Tag ^& date of installed release
echo   %USERPROFILE%\.oculix-mcp\
echo     journal\                Signed audit chain (Ed25519)
echo     public.key / private.key
echo.
echo HOW TO CALL AN MCP TOOL FROM A SCRIPT (STDIO MODE)
echo --------------------------------------------------
echo The server speaks JSON-RPC 2.0 line-by-line.
echo Handshake once, then call tools/call with tool name + args.
echo.
echo Copy-paste this in a CMD window to take a screenshot:
echo.
echo   (
echo     echo {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"cli","version":"1"}}}
echo     echo {"jsonrpc":"2.0","method":"notifications/initialized"}
echo     echo {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"oculix_screenshot","arguments":{"screen_index":0}}}
echo   ) ^| "%JAVA%" -jar "%MCP_JAR%" run
echo.
echo The response contains a base64 PNG in result.content[0].data.
echo.
echo HOW TO CALL AN MCP TOOL OVER HTTP (SERVE MODE)
echo ----------------------------------------------
echo Start the server with menu item [3], then POST:
echo.
echo   curl -X POST http://127.0.0.1:7337/mcp ^^
echo        -H "Content-Type: application/json" ^^
echo        -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"oculix_screenshot\",\"arguments\":{}}}"
echo.
echo COMMON TOOL CALLS (arguments only)
echo ----------------------------------
echo   oculix_screenshot         {"screen_index": 0}
echo   oculix_find_image         {"image": "C:\path\to\ref.png"}
echo   oculix_click_image        {"image": "C:\path\to\ref.png"}
echo   oculix_find_text          {"text": "Login"}
echo   oculix_click_text         {"text": "Login"}
echo   oculix_type_text          {"text": "hello world"}
echo   oculix_key_combo          {"combo": "Ctrl+C"}
echo   oculix_wait_for_stable    {"region": {...}, "timeout_ms": 3000}
echo.
echo Full list with detailed argument schemas via item [T].
echo.
echo AUDIT ^& SECURITY
echo -----------------
echo Every tool call is signed and chained (Ed25519).
echo Verify chain integrity via item [8].
echo Rotate the signing key via item [7].
echo.
echo TROUBLESHOOTING
echo ---------------
echo   - Nothing happens on item [1]/[2]/[3]  -^> install via [6] first
echo   - Colours look weird                    -^> open in Windows Terminal
echo   - "was not expected" error              -^> check the .bat file has
echo                                              CRLF line endings
echo.
echo ==================================================
goto :pause_and_menu

:quit
cls
echo.
echo   %C_MAGENTA%🦎  See you.%C_RESET%
echo.
endlocal
exit /b 0


REM ============================================================
REM  Install / Update flow
REM ============================================================

:install
cls
echo.
echo   %C_MAGENTA%╔══════════════════════════════════════════════╗%C_RESET%
echo   %C_MAGENTA%║%C_RESET%  %C_BOLD%🦎  Install / Update%C_RESET%                        %C_MAGENTA%║%C_RESET%
echo   %C_MAGENTA%╚══════════════════════════════════════════════╝%C_RESET%
echo.
echo   %C_CYAN%[info]%C_RESET% Fetching available releases from GitHub...
echo.

set "TMPLIST=%TEMP%\oculix-releases.txt"
if exist "%TMPLIST%" del "%TMPLIST%"

powershell -NoProfile -Command ^
  "try { $r = Invoke-RestMethod -Uri '%GH_API%?per_page=8' -Headers @{'User-Agent'='oculix-manager'};" ^
  "  $i = 0;" ^
  "  foreach ($rel in $r) {" ^
  "    $ide = $rel.assets | Where-Object { $_.name -match '^oculixide-.*-windows\.jar$' } | Select-Object -First 1;" ^
  "    $mcp = $rel.assets | Where-Object { $_.name -match '^oculix-mcp-server-.*\.jar$' } | Select-Object -First 1;" ^
  "    if ($ide -and $mcp) {" ^
  "      $i++;" ^
  "      $tag = $rel.tag_name;" ^
  "      $date = ([DateTime]$rel.published_at).ToString('yyyy-MM-dd');" ^
  "      $flag = if ($rel.prerelease) { 'RC' } elseif ($i -eq 1) { 'ST' } else { '  ' };" ^
  "      $line = '{0}|{1}|{2}|{3}|{4}|{5}' -f $i, $tag, $flag, $date, $ide.browser_download_url, $mcp.browser_download_url;" ^
  "      Add-Content -Path '%TMPLIST%' -Value $line -Encoding UTF8;" ^
  "    }" ^
  "  }" ^
  "} catch { Write-Host ('ERROR: ' + $_.Exception.Message); exit 1 }"

if not exist "%TMPLIST%" (
  echo   %C_RED%[error]%C_RESET% Could not reach GitHub API. Check your connection.
  goto :pause_and_menu
)

set "DEFAULT_IDX="
echo   %C_BOLD%Available releases:%C_RESET%
echo.
for /F "usebackq tokens=1-6 delims=|" %%A in ("%TMPLIST%") do (
  set "ROW_%%A_TAG=%%B"
  set "ROW_%%A_IDE=%%E"
  set "ROW_%%A_MCP=%%F"
  set "LABEL=%%C"
  if /I "!LABEL!"=="ST" (
    if not defined DEFAULT_IDX set "DEFAULT_IDX=%%A"
    echo     %C_GREEN%[%%A]%C_RESET% %C_BOLD%%%B%C_RESET%   %C_GREEN%^(stable — recommended^)%C_RESET%   %C_DIM%%%D%C_RESET%
  ) else if /I "!LABEL!"=="RC" (
    echo     %C_YELLOW%[%%A]%C_RESET% %C_BOLD%%%B%C_RESET%   %C_YELLOW%^(release candidate^)%C_RESET%      %C_DIM%%%D%C_RESET%
  ) else (
    echo     [%%A] %%B                                     %C_DIM%%%D%C_RESET%
  )
)
echo.

if not defined DEFAULT_IDX set "DEFAULT_IDX=1"

set /p "CHOICE=  Choose a version [ENTER = %DEFAULT_IDX%]: "
if not defined CHOICE set "CHOICE=%DEFAULT_IDX%"

call set "JAR_TAG=%%ROW_%CHOICE%_TAG%%"
call set "IDE_URL=%%ROW_%CHOICE%_IDE%%"
call set "MCP_URL=%%ROW_%CHOICE%_MCP%%"
if not defined IDE_URL (
  echo   %C_RED%[error]%C_RESET% Invalid choice.
  del "%TMPLIST%" 2>nul
  goto :pause_and_menu
)
del "%TMPLIST%" 2>nul

echo.
echo   %C_CYAN%[info]%C_RESET% Selected: %C_BOLD%%JAR_TAG%%C_RESET%
if exist "%BASE%" (
  echo   %C_CYAN%[info]%C_RESET% Cohabitation-safe: only installer artefacts will be overwritten.
)
echo.

mkdir "%BASE%" 2>nul
mkdir "%BASE%\jre" 2>nul

REM --- Generate the gecko-animated downloader PowerShell helper ---
set "DL_PS1=%TEMP%\oculix-gecko-dl.ps1"
call :write_downloader

REM --- [1/4] JRE ---
echo   %C_CYAN%[1/4]%C_RESET% Downloading Temurin JRE 21...
powershell -NoProfile -ExecutionPolicy Bypass -File "%DL_PS1%" "%JRE_URL%" "%TEMP%\oculix-jre.zip"
if errorlevel 1 (
  echo   %C_RED%[error]%C_RESET% JRE download failed.
  del "%DL_PS1%" 2>nul
  goto :pause_and_menu
)

echo   %C_CYAN%[2/4]%C_RESET% Extracting JRE...
tar -xf "%TEMP%\oculix-jre.zip" -C "%BASE%\jre" --strip-components=1
del "%TEMP%\oculix-jre.zip" 2>nul

REM --- [3/4] IDE ---
echo   %C_CYAN%[3/4]%C_RESET% Downloading OculiX IDE %JAR_TAG%...
powershell -NoProfile -ExecutionPolicy Bypass -File "%DL_PS1%" "%IDE_URL%" "%IDE_JAR%"
if errorlevel 1 (
  echo   %C_RED%[error]%C_RESET% IDE download failed.
  del "%DL_PS1%" 2>nul
  goto :pause_and_menu
)

REM --- [4/4] MCP ---
echo   %C_CYAN%[4/4]%C_RESET% Downloading OculiX MCP %JAR_TAG%...
powershell -NoProfile -ExecutionPolicy Bypass -File "%DL_PS1%" "%MCP_URL%" "%MCP_JAR%"
if errorlevel 1 (
  echo   %C_RED%[error]%C_RESET% MCP download failed.
  del "%DL_PS1%" 2>nul
  goto :pause_and_menu
)

del "%DL_PS1%" 2>nul

REM --- Icon (best-effort) ---
curl -sL --fail -o "%ICON%" "%ICON_URL%" 2>nul

REM --- Shortcuts ---
echo   %C_CYAN%[+]%C_RESET% Creating desktop shortcuts...
powershell -NoProfile -Command ^
  "$desktop = [Environment]::GetFolderPath('Desktop');" ^
  "$ide = (New-Object -COM WScript.Shell).CreateShortcut($desktop + '\OculiX.lnk');" ^
  "$ide.TargetPath = '%JAVAW%';" ^
  "$ide.Arguments = '-jar \"%IDE_JAR%\"';" ^
  "$ide.WorkingDirectory = '%BASE%';" ^
  "if (Test-Path '%ICON%') { $ide.IconLocation = '%ICON%' };" ^
  "$ide.Description = 'OculiX IDE %JAR_TAG% — visual automation';" ^
  "$ide.Save();" ^
  "$mcp = (New-Object -COM WScript.Shell).CreateShortcut($desktop + '\OculiX MCP.lnk');" ^
  "$mcp.TargetPath = '%JAVA%';" ^
  "$mcp.Arguments = '-jar \"%MCP_JAR%\" run';" ^
  "$mcp.WorkingDirectory = '%BASE%';" ^
  "if (Test-Path '%ICON%') { $mcp.IconLocation = '%ICON%' };" ^
  "$mcp.Description = 'OculiX MCP %JAR_TAG% — close window to stop';" ^
  "$mcp.Save()"

REM --- install.info ---
(
  echo tag=%JAR_TAG%
  echo date=%DATE%
) > "%INFO%"

echo.
echo   %C_GREEN%[done]%C_RESET% %C_BOLD%%JAR_TAG%%C_RESET% installed.
echo.
echo   Shortcuts on your Desktop:
echo     - %C_BOLD%OculiX%C_RESET%      %C_DIM%(IDE)%C_RESET%
echo     - %C_BOLD%OculiX MCP%C_RESET%  %C_DIM%(server; close window to stop)%C_RESET%
echo.
goto :pause_and_menu


REM ============================================================
REM  Write the PowerShell downloader with gecko-lap animation
REM ============================================================

:write_downloader
(
  echo param^([string]$url, [string]$dest^)
  echo $ErrorActionPreference = 'Stop'
  echo $gecko = [char]::ConvertFromUtf32^(0x1F98E^)
  echo $barWidth = 40
  echo try {
  echo   $req = [System.Net.HttpWebRequest]::Create^($url^)
  echo   $req.UserAgent = 'oculix-manager'
  echo   $req.AllowAutoRedirect = $true
  echo   $resp = $req.GetResponse^(^)
  echo   $total = $resp.ContentLength
  echo   $stream = $resp.GetResponseStream^(^)
  echo   $file = [System.IO.File]::Open^($dest, 'Create'^)
  echo   $buf = New-Object byte[] 65536
  echo   $read = 0L
  echo   $chunkCount = 0
  echo   $start = Get-Date
  echo   $lastReport = $start
  echo   $lastRead = 0L
  echo   $speed = 0.0
  echo   while ^(^($n = $stream.Read^($buf, 0, $buf.Length^)^) -gt 0^) {
  echo     $file.Write^($buf, 0, $n^)
  echo     $read += $n
  echo     $chunkCount++
  echo     $now = Get-Date
  echo     $dt = ^($now - $lastReport^).TotalSeconds
  echo     if ^($dt -ge 0.1^) {
  echo       $speed = ^(^($read - $lastRead^) / $dt^) / 1MB
  echo       $lastReport = $now
  echo       $lastRead = $read
  echo       $pct = if ^($total -gt 0^) { [int]^(^($read / $total^) * 100^) } else { 0 }
  echo       $pos = $chunkCount %% $barWidth
  echo       $before = ' ' * $pos
  echo       $after = ' ' * ^($barWidth - $pos - 1^)
  echo       $line = "`r  [{0}{1}{2}] {3,3}%%   {4,5:N1} MB/s" -f $before, $gecko, $after, $pct, $speed
  echo       [Console]::Write^($line^)
  echo     }
  echo   }
  echo   $bar = ' ' * ^($barWidth - 1^) + $gecko
  echo   $mb = $read / 1MB
  echo   $line = "`r  [{0}] 100%%   {1,6:N1} MB   [OK]                    " -f $bar, $mb
  echo   [Console]::WriteLine^($line^)
  echo }
  echo finally {
  echo   if ^($file^) { $file.Close^(^) }
  echo   if ^($stream^) { $stream.Close^(^) }
  echo }
) > "%DL_PS1%"
exit /b


REM ============================================================
REM  Write the PowerShell MCP tools/list caller (JSON-RPC over stdio)
REM ============================================================

:write_tools_lister
(
  echo param^([string]$java, [string]$jar^)
  echo $ErrorActionPreference = 'Stop'
  echo $psi = New-Object System.Diagnostics.ProcessStartInfo
  echo $psi.FileName = $java
  echo $psi.Arguments = '-jar "' + $jar + '" run'
  echo $psi.RedirectStandardInput = $true
  echo $psi.RedirectStandardOutput = $true
  echo $psi.RedirectStandardError = $true
  echo $psi.UseShellExecute = $false
  echo $psi.CreateNoWindow = $true
  echo $p = [System.Diagnostics.Process]::Start^($psi^)
  echo try {
  echo   $init = '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"oculix-manager","version":"1.0"}}}'
  echo   $p.StandardInput.WriteLine^($init^)
  echo   $p.StandardInput.Flush^(^)
  echo   $initLine = $p.StandardOutput.ReadLine^(^)
  echo   $p.StandardInput.WriteLine^('{"jsonrpc":"2.0","method":"notifications/initialized"}'^)
  echo   $p.StandardInput.WriteLine^('{"jsonrpc":"2.0","id":2,"method":"tools/list"}'^)
  echo   $p.StandardInput.Flush^(^)
  echo   $toolsLine = $null
  echo   for ^($i = 0; $i -lt 20; $i++^) {
  echo     $line = $p.StandardOutput.ReadLine^(^)
  echo     if ^($null -eq $line^) { break }
  echo     if ^($line -match '"id"\s*:\s*2'^) { $toolsLine = $line; break }
  echo   }
  echo   if ^($null -eq $toolsLine^) {
  echo     Write-Host 'ERROR: no tools/list response received from server.' -ForegroundColor Red
  echo     Write-Host 'Server stderr:'
  echo     Write-Host $p.StandardError.ReadToEnd^(^)
  echo     exit 1
  echo   }
  echo   $parsed = $toolsLine ^| ConvertFrom-Json
  echo   $tools = $parsed.result.tools
  echo   Write-Host 'MCP tools available on this server:'
  echo   Write-Host '===================================='
  echo   foreach ^($t in $tools^) {
  echo     $desc = if ^($t.description^) { $t.description } else { '' }
  echo     Write-Host ^(''.PadRight^(2^) + $t.name.PadRight^(30^) + $desc^)
  echo   }
  echo   Write-Host ''
  echo   Write-Host ^('{0} tools registered.' -f $tools.Count^)
  echo }
  echo finally {
  echo   try { $p.StandardInput.Close^(^) } catch { }
  echo   if ^(-not $p.WaitForExit^(2000^)^) { $p.Kill^(^) }
  echo }
) > "%TOOLS_PS1%"
exit /b
