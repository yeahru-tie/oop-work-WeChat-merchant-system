param(
    [Parameter(Mandatory = $true)][string]$Directory,
    [Parameter(Mandatory = $true)][string]$Archive
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$sourceDirectory = (Resolve-Path -LiteralPath $Directory).Path.TrimEnd('\')
$archiveFile = [IO.Path]::GetFullPath($Archive)
if (Test-Path -LiteralPath $archiveFile) { throw 'Archive already exists. Choose a new output file.' }
if ($archiveFile.StartsWith($sourceDirectory + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Archive cannot be inside the source directory.' }
$outputStream = [IO.File]::Open($archiveFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
try {
    $zip = [IO.Compression.ZipArchive]::new($outputStream, [IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        $prefix = [IO.Path]::GetFileName($sourceDirectory)
        foreach ($file in (Get-ChildItem -LiteralPath $sourceDirectory -Recurse -File | Sort-Object FullName)) {
            $relativeName = $prefix + '/' + $file.FullName.Substring($sourceDirectory.Length + 1).Replace('\', '/')
            $entry = $zip.CreateEntry($relativeName, [IO.Compression.CompressionLevel]::Optimal)
            $inputStream = $null
            for ($attempt = 0; $attempt -lt 4; $attempt++) {
                try { $inputStream = [IO.File]::Open($file.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite); break }
                catch [IO.IOException] { if ($attempt -eq 3) { throw }; Start-Sleep -Milliseconds 250 }
            }
            try {
                $entryStream = $entry.Open()
                try { $inputStream.CopyTo($entryStream) } finally { $entryStream.Dispose() }
            } finally { if ($null -ne $inputStream) { $inputStream.Dispose() } }
        }
    } finally { $zip.Dispose() }
} finally { $outputStream.Dispose() }
