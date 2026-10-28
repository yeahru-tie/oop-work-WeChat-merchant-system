param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('client', 'server')]
    [string]$App,
    [ValidateRange(1, 65535)]
    [int]$Port,
    [ValidateNotNullOrEmpty()]
    [string]$DatabasePath
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain.json') -Raw | ConvertFrom-Json
$javaExecutable = Join-Path $repositoryRoot ('.tools/' + $config.jdk.directory + '/bin/java.exe')
$applicationJar = Join-Path $repositoryRoot ($App + '/target/merchant-' + $App + '.jar')

if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
    throw 'Local JDK is missing. Run scripts/setup-toolchain.ps1 first.'
}
if (-not (Test-Path -LiteralPath $applicationJar -PathType Leaf)) {
    throw 'Application JAR is missing. Run scripts/maven.ps1 verify from the repository root first.'
}

$javaArguments = @()
if ($App -eq 'server') {
    $resolvedDatabase = if ($PSBoundParameters.ContainsKey('DatabasePath')) { $DatabasePath } else { 'data/merchant.db' }
    if (-not [IO.Path]::IsPathRooted($resolvedDatabase)) {
        $resolvedDatabase = Join-Path $repositoryRoot $resolvedDatabase
    }
    $javaArguments += '--enable-native-access=ALL-UNNAMED'
    $javaArguments += ('-Dmerchant.database=' + [IO.Path]::GetFullPath($resolvedDatabase))
} elseif ($PSBoundParameters.ContainsKey('DatabasePath')) {
    throw 'DatabasePath applies to the server only.'
}
if ($PSBoundParameters.ContainsKey('Port')) {
    $javaArguments += "-Dmerchant.port=$Port"
}
if ($App -eq 'client') {
    $javaArguments += ('-Dmerchant.clientState=' + (Join-Path $repositoryRoot 'data/client-pending'))
}
& $javaExecutable @javaArguments -jar $applicationJar
exit $LASTEXITCODE
