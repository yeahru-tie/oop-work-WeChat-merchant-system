param([switch]$Offline)

$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne 'Win32NT' -or -not [Environment]::Is64BitOperatingSystem) {
    throw 'This pinned toolchain requires Windows x64.'
}
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot '.tools'))
$downloadsRoot = Join-Path $toolsRoot 'downloads'
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain.json') -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Path $downloadsRoot -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

foreach ($tool in @($config.jdk, $config.maven)) {
    $archivePath = [IO.Path]::GetFullPath((Join-Path $downloadsRoot $tool.archive))
    $toolDirectory = [IO.Path]::GetFullPath((Join-Path $toolsRoot $tool.directory))
    if (-not $archivePath.StartsWith($downloadsRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or
        -not $toolDirectory.StartsWith($toolsRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Toolchain manifest contains an out-of-workspace path.'
    }
    $archiveIsValid = (Test-Path -LiteralPath $archivePath -PathType Leaf) -and
        ((Get-FileHash -LiteralPath $archivePath -Algorithm $tool.algorithm).Hash -eq $tool.hash)
    if (-not $archiveIsValid) {
        if ($Offline) { throw ('Verified archive is unavailable offline: ' + $tool.archive) }
        Write-Host ('Downloading ' + $tool.archive)
        & curl.exe --fail --location --silent --show-error --retry 2 --connect-timeout 20 --max-time 600 --output $archivePath $tool.url
        if ($LASTEXITCODE -ne 0) { throw ('Download failed: ' + $tool.archive) }
        if ((Get-FileHash -LiteralPath $archivePath -Algorithm $tool.algorithm).Hash -ne $tool.hash) {
            throw ('Checksum mismatch; extraction refused: ' + $tool.archive)
        }
    }
    Write-Host ('Verified ' + $tool.algorithm + ': ' + $tool.archive)

    $entryPoint = Join-Path $toolDirectory $tool.entryPoint
    if (-not (Test-Path -LiteralPath $entryPoint -PathType Leaf)) {
        if (Test-Path -LiteralPath $toolDirectory) {
            throw ('Incomplete tool directory; inspect it before retrying: ' + $toolDirectory)
        }
        $archive = [IO.Compression.ZipFile]::OpenRead($archivePath)
        try {
            foreach ($entry in $archive.Entries) {
                $destination = [IO.Path]::GetFullPath((Join-Path $toolsRoot $entry.FullName))
                if (-not $destination.StartsWith($toolDirectory + '\', [StringComparison]::OrdinalIgnoreCase) -and
                    $destination.TrimEnd('\') -ne $toolDirectory) {
                    throw ('Unexpected archive path: ' + $entry.FullName)
                }
            }
        }
        finally { $archive.Dispose() }
        Write-Host ('Extracting ' + $tool.archive)
        Expand-Archive -LiteralPath $archivePath -DestinationPath $toolsRoot
    }
    if (-not (Test-Path -LiteralPath $entryPoint -PathType Leaf)) {
        throw ('Expected executable is missing: ' + $entryPoint)
    }
}

$javaHome = Join-Path $toolsRoot $config.jdk.directory
& (Join-Path $javaHome 'bin/java.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'java -version failed.' }
& (Join-Path $javaHome 'bin/javac.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'javac -version failed.' }
& (Join-Path $PSScriptRoot 'maven.ps1') --version
if ($LASTEXITCODE -ne 0) { throw 'Maven version check failed.' }
Write-Host 'Repository-local toolchain is ready. No system settings were changed.'
