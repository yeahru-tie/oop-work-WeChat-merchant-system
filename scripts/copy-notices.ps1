param([Parameter(Mandatory = $true)][string]$RepositoryRoot, [Parameter(Mandatory = $true)][string]$Destination)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$application = [IO.Compression.ZipFile]::OpenRead((Join-Path $RepositoryRoot 'server/target/merchant-server.jar'))
$coordinates = @()
try {
    foreach ($metadata in @($application.Entries | Where-Object { $_.FullName -match '^META-INF/maven/[^/]+/[^/]+/pom.properties$' })) {
        $reader = [IO.StreamReader]::new($metadata.Open())
        try { $properties = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $group = [regex]::Match($properties, '(?m)^groupId=([^\r\n]+)').Groups[1].Value
        $artifact = [regex]::Match($properties, '(?m)^artifactId=([^\r\n]+)').Groups[1].Value
        $version = [regex]::Match($properties, '(?m)^version=([^\r\n]+)').Groups[1].Value
        if ($group -eq 'cn.edu.jlu.wechatmerchant') { continue }
        if (-not $group -or -not $artifact -or -not $version) { throw 'Dependency metadata is incomplete.' }
        $coordinates += ($group + ':' + $artifact + ':' + $version)
        $stem = $artifact + '-' + $version
        $cached = Join-Path $RepositoryRoot ('.tools/m2/repository/' + $group.Replace('.', '/') + '/' + $artifact + '/' + $version + '/' + $stem)
        $target = Join-Path $Destination $stem
        New-Item -ItemType Directory -Path $target -Force | Out-Null
        Copy-Item -LiteralPath ($cached + '.pom') -Destination $target
        $dependency = [IO.Compression.ZipFile]::OpenRead(($cached + '.jar'))
        try {
            foreach ($entry in @($dependency.Entries | Where-Object { $_.Name -match '(?i)(license|notice|copying)' })) {
                $entryTarget = [IO.Path]::GetFullPath((Join-Path $target $entry.FullName))
                if (-not $entryTarget.StartsWith([IO.Path]::GetFullPath($target) + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected notice path.' }
                New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($entryTarget)) -Force | Out-Null
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $entryTarget)
            }
        } finally { $dependency.Dispose() }
    }
} finally { $application.Dispose() }
[IO.File]::WriteAllText((Join-Path $Destination 'DEPENDENCIES.txt'), (($coordinates | Sort-Object -Unique) -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
