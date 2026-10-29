param(
    [Parameter(Mandatory = $true)][ValidateSet('client', 'server')][string]$App,
    [ValidateRange(1, 65535)][int]$Port,
    [string]$DatabasePath
)
$ErrorActionPreference = 'Stop'
$releaseRoot = Split-Path -Parent $PSScriptRoot
$javaExecutable = Join-Path $releaseRoot 'runtime/bin/java.exe'
$applicationJar = Join-Path $releaseRoot ('app/merchant-' + $App + '.jar')
$javaArguments = @()
if ($App -eq 'server') {
    $databaseFile = if ($PSBoundParameters.ContainsKey('DatabasePath')) { $DatabasePath } else { 'data/merchant.db' }
    if ([string]::IsNullOrWhiteSpace($databaseFile)) { throw 'DatabasePath cannot be blank.' }
    if (-not [IO.Path]::IsPathRooted($databaseFile)) { $databaseFile = Join-Path $releaseRoot $databaseFile }
    $javaArguments += @('--enable-native-access=ALL-UNNAMED', ('-Dmerchant.database=' + [IO.Path]::GetFullPath($databaseFile)))
} elseif ($PSBoundParameters.ContainsKey('DatabasePath')) { throw 'DatabasePath applies to the server only.' }
if ($PSBoundParameters.ContainsKey('Port')) { $javaArguments += ('-Dmerchant.port=' + $Port) }
$javaArguments += ('-Dmerchant.clientState=' + (Join-Path $releaseRoot 'data/client-pending'))
$javaArguments += ('-Dmerchant.themeFile=' + (Join-Path $releaseRoot ('data/' + $App + '-theme.txt')))
& $javaExecutable @javaArguments -jar $applicationJar
exit $LASTEXITCODE
