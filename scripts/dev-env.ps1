# Dot-source this file to enable repository-local tools in this process only.
$toolchainRoot = Split-Path -Parent $PSScriptRoot
$toolchainConfig = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain.json') -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
$localJavaHome = Join-Path $toolchainRoot ('.tools/' + $toolchainConfig.jdk.directory)
$localMavenHome = Join-Path $toolchainRoot ('.tools/' + $toolchainConfig.maven.directory)

foreach ($requiredTool in @((Join-Path $localJavaHome 'bin/java.exe'), (Join-Path $localJavaHome 'bin/javac.exe'), (Join-Path $localMavenHome 'bin/mvn.cmd'))) {
    if (-not (Test-Path -LiteralPath $requiredTool -PathType Leaf)) {
        throw 'Local toolchain is missing. Run scripts/setup-toolchain.ps1 first.'
    }
}

$localToolBins = @((Join-Path $localJavaHome 'bin'), (Join-Path $localMavenHome 'bin'))
$remainingPath = @($env:Path -split ';' | Where-Object { $_ -and $_ -notin $localToolBins })
$env:JAVA_HOME = $localJavaHome
$env:MAVEN_HOME = $localMavenHome
$env:Path = ($localToolBins + $remainingPath) -join ';'
Write-Host 'Repository-local Java and Maven enabled for this PowerShell process.'
