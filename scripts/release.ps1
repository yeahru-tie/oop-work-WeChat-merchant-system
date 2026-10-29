param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$buildArguments = @('--batch-mode', '--no-transfer-progress', 'verify')
if ($Offline) { $buildArguments = @('--offline') + $buildArguments }
Push-Location -LiteralPath $repositoryRoot
try {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'maven.ps1') @buildArguments
    if ($LASTEXITCODE -ne 0) { throw 'Verification failed. No release was created.' }
} finally { Pop-Location }
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain.json') -Raw | ConvertFrom-Json
$jdkDirectory = Join-Path $repositoryRoot ('.tools/' + $config.jdk.directory)
$releaseRoot = Join-Path $repositoryRoot ('dist/merchant-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
foreach ($directory in @('app', 'scripts', 'source')) { New-Item -ItemType Directory -Path (Join-Path $releaseRoot $directory) | Out-Null }
foreach ($appName in @('client', 'server')) {
    Copy-Item -LiteralPath (Join-Path $repositoryRoot ($appName + '/target/merchant-' + $appName + '.jar')) -Destination (Join-Path $releaseRoot 'app')
}
& (Join-Path $jdkDirectory 'bin/jlink.exe') --add-modules java.desktop,java.sql,java.management,jdk.unsupported,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --output (Join-Path $releaseRoot 'runtime')
if ($LASTEXITCODE -ne 0) { throw 'Runtime packaging failed.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'run-release.ps1') -Destination (Join-Path $releaseRoot 'scripts/run.ps1')
$developmentLogName = -join [char[]](0x5f00, 0x53d1, 0x65e5, 0x5fd7)
foreach ($directory in @('docs', 'reports', 'scripts', $developmentLogName)) {
    Copy-Item -LiteralPath (Join-Path $repositoryRoot $directory) -Destination (Join-Path $releaseRoot 'source') -Recurse
}
foreach ($module in @('client', 'server', 'desktop', 'protocol')) {
    $moduleDestination = Join-Path $releaseRoot ('source/' + $module)
    New-Item -ItemType Directory -Path $moduleDestination | Out-Null
    Copy-Item -LiteralPath (Join-Path $repositoryRoot ($module + '/pom.xml')) -Destination $moduleDestination
    Copy-Item -LiteralPath (Join-Path $repositoryRoot ($module + '/src')) -Destination $moduleDestination -Recurse
}
foreach ($file in @('pom.xml', 'README.md', 'AGENTS.md', '.gitignore', '.gitattributes')) {
    Copy-Item -LiteralPath (Join-Path $repositoryRoot $file) -Destination (Join-Path $releaseRoot 'source')
}
$deliveryFileName = (-join [char[]](0x4ea4, 0x4ed8, 0x4e0e, 0x6f14, 0x793a)) + '.md'
Copy-Item -LiteralPath (Join-Path $repositoryRoot ('docs/' + $deliveryFileName)) -Destination (Join-Path $releaseRoot 'README.md')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'toolchain.json') -Destination (Join-Path $releaseRoot 'toolchain.json')
& (Join-Path $PSScriptRoot 'copy-notices.ps1') -RepositoryRoot $repositoryRoot -Destination (Join-Path $releaseRoot 'third-party')
$hashLines = Get-ChildItem -LiteralPath $releaseRoot -Recurse -File | Sort-Object FullName | ForEach-Object {
    $relativePath = $_.FullName.Substring($releaseRoot.Length + 1).Replace('\', '/')
    (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $relativePath
}
[IO.File]::WriteAllText((Join-Path $releaseRoot 'SHA256SUMS.txt'), (($hashLines -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
$archive = $releaseRoot + '.zip'
& (Join-Path $PSScriptRoot 'pack-directory.ps1') -Directory $releaseRoot -Archive $archive
$archiveHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText(($archive + '.sha256'), ($archiveHash + '  ' + [IO.Path]::GetFileName($archive) + "`n"), [Text.UTF8Encoding]::new($false))
Write-Host ('Release: ' + $archive)
Write-Host ('SHA256: ' + $archiveHash)
