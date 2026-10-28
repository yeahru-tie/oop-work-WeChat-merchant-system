param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('init', 'list')]
    [string]$Action,
    [ValidateNotNullOrEmpty()]
    [string]$DatabasePath = 'data/merchant.db'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain.json') -Raw | ConvertFrom-Json
$javaExecutable = Join-Path $repositoryRoot ('.tools/' + $config.jdk.directory + '/bin/java.exe')
$serverJar = Join-Path $repositoryRoot 'server/target/merchant-server.jar'
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf) -or -not (Test-Path -LiteralPath $serverJar -PathType Leaf)) {
    throw 'Build the server with scripts/maven.ps1 verify before using the database tool.'
}
if (-not [IO.Path]::IsPathRooted($DatabasePath)) {
    $DatabasePath = Join-Path $repositoryRoot $DatabasePath
}
$resolvedDatabase = [IO.Path]::GetFullPath($DatabasePath)
$previousOutputEncoding = [Console]::OutputEncoding
try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
    & $javaExecutable --enable-native-access=ALL-UNNAMED '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' "-Dmerchant.database=$resolvedDatabase" -cp $serverJar cn.edu.jlu.wechatmerchant.server.cli.DatabaseCli $Action
    $databaseExitCode = $LASTEXITCODE
} finally {
    [Console]::OutputEncoding = $previousOutputEncoding
}
exit $databaseExitCode
