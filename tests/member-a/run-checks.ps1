param(
    [ValidateSet('H2','MySQL','Menu')][string]$Mode = 'H2',
    [string]$ConfigPath,
    [string]$DriverJar
)
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$buildDir = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
$javaSources = @(Get-ChildItem (Join-Path $projectRoot 'app/src/main/java/edu/project/student') -Recurse -Filter '*.java' | ForEach-Object FullName)
$javaSources += @(Get-ChildItem $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -d $buildDir @javaSources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
if (-not $DriverJar) {
    $driverDir = if ($Mode -eq 'H2') { 'com/h2database/h2' } else { 'com/mysql/mysql-connector-j' }
    $cachedDriver = Get-ChildItem (Join-Path $env:USERPROFILE ".m2/repository/$driverDir") -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object FullName -Descending | Select-Object -First 1
    if (-not $cachedDriver) { throw '请用-DriverJar指定H2或MySQL JDBC驱动jar的路径' }
    $DriverJar = $cachedDriver.FullName
}
$DriverJar = (Resolve-Path -LiteralPath $DriverJar).Path
$runArgs = @('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',"$buildDir;$DriverJar")
if ($Mode -eq 'Menu') {
    if (-not $ConfigPath) { throw 'Menu模式需要-ConfigPath' }
    $runArgs += @('edu.project.student.StandaloneLauncher',(Resolve-Path -LiteralPath $ConfigPath).Path)
    & java @runArgs
} else {
    $runArgs += @('edu.project.student.ModuleChecks',$projectRoot)
    if ($Mode -eq 'MySQL') {
        if (-not $ConfigPath) { throw 'MySQL模式需要-ConfigPath，目标为独立空测试库member_a_test_开头' }
        $runArgs += (Resolve-Path -LiteralPath $ConfigPath).Path
    }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $logPath = Join-Path $projectRoot "evidence/member-a/$($Mode.ToLower())-$stamp.log"
    & java @runArgs 2>&1 | Tee-Object -FilePath $logPath
    if ($LASTEXITCODE -ne 0) { throw "Checks failed; log: $logPath" }
    Write-Output "Evidence: $logPath"
}
if ($LASTEXITCODE -ne 0) { throw 'Java process failed' }
