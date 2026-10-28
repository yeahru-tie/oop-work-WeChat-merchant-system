# Forward all arguments to pinned Maven, keeping the environment and cache local.
$ErrorActionPreference = 'Stop'
$mavenArguments = @($args)
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$savedJavaHome = $env:JAVA_HOME
$savedMavenHome = $env:MAVEN_HOME
$savedPath = $env:Path
$savedSkipRc = $env:MAVEN_SKIP_RC
$mavenExitCode = 1
try {
    . (Join-Path $PSScriptRoot 'dev-env.ps1')
    $env:MAVEN_SKIP_RC = 'true'
    $localRepository = Join-Path $repositoryRoot '.tools/m2/repository'
    & (Join-Path $env:MAVEN_HOME 'bin/mvn.cmd') "-Dmaven.repo.local=$localRepository" @mavenArguments
    $mavenExitCode = $LASTEXITCODE
}
finally {
    $env:JAVA_HOME = $savedJavaHome
    $env:MAVEN_HOME = $savedMavenHome
    $env:Path = $savedPath
    $env:MAVEN_SKIP_RC = $savedSkipRc
}
exit $mavenExitCode
