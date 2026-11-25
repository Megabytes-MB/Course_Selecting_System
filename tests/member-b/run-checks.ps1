param(
    [Parameter(Mandatory=$true)][string]$ConfigPath,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$privateConfig = (Resolve-Path -LiteralPath $ConfigPath).Path
if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"
}
& mvn -B -f (Join-Path $projectRoot 'app/pom.xml') package
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
$buildDir = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
$classPath = "$(Join-Path $projectRoot 'app/target/classes');$(Join-Path $projectRoot 'app/target/dependency/*')"
& javac --release 17 -encoding UTF-8 -cp $classPath -d $buildDir (Join-Path $PSScriptRoot 'ModuleChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& java '-Dfile.encoding=UTF-8' "-Ddb.config=$privateConfig" -cp "$buildDir;$classPath" edu.project.catalog.checks.ModuleChecks $projectRoot
if ($LASTEXITCODE -ne 0) { throw 'B checks failed' }
