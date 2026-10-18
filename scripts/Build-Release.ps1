[CmdletBinding()]
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskProperties = Get-Content -LiteralPath (Join-Path $taskRoot 'gradle.properties') -Raw | ConvertFrom-StringData
$taskVersion = $taskProperties.mod_version
$taskMinecraft = $taskProperties.minecraft_version
if ($taskVersion -notmatch '^\d+\.\d+\.\d+(?:[-+][\w.-]+)?$') { throw 'Invalid mod_version' }
$taskFileName = "findmeclientside-forge-$taskMinecraft-$taskVersion.jar"
$taskJarPath = Join-Path $taskRoot "build/libs/$taskFileName"

if (!$SkipBuild) {
    Push-Location $taskRoot
    try {
        if ($env:OS -eq 'Windows_NT') { & ./gradlew.bat --console=plain build }
        else { & ./gradlew --no-daemon --console=plain build }
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
    } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $taskJarPath)) { throw "Missing release JAR: $taskJarPath" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskJar = [IO.Compression.ZipFile]::OpenRead($taskJarPath)
try {
    $taskEntries = @($taskJar.Entries | ForEach-Object FullName)
    foreach ($taskRequired in @('META-INF/mods.toml', 'findmeclientside.refmap.json', 'findmeclientside.mixins.json', 'icon.png', 'LICENSE', 'LICENSES/Lucide.txt')) {
        if ($taskEntries -notcontains $taskRequired) { throw "JAR missing $taskRequired" }
    }
    if (@($taskEntries | Where-Object { $_ -like 'dev/emi/*' -or $_ -like 'dev/findmeclientside/qa/*' }).Count) {
        throw 'Release JAR includes EMI classes or a temporary QA fixture'
    }
} finally { $taskJar.Dispose() }

$taskDist = [IO.Path]::GetFullPath((Join-Path $taskRoot 'dist'))
$taskPackage = [IO.Path]::GetFullPath((Join-Path $taskDist "modrinth-$taskVersion"))
$taskExpectedParent = $taskDist + [IO.Path]::DirectorySeparatorChar
if (!$taskPackage.StartsWith($taskExpectedParent, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Release directory escaped dist'
}
Write-Output "Release directory verified: $taskPackage"
if (Test-Path -LiteralPath $taskPackage) { Remove-Item -LiteralPath $taskPackage -Recurse -Force }
New-Item -ItemType Directory -Force $taskPackage | Out-Null
Copy-Item -LiteralPath $taskJarPath -Destination $taskPackage
Copy-Item -LiteralPath (Join-Path $taskRoot 'publishing/modrinth') -Destination $taskPackage -Recurse
Copy-Item -LiteralPath (Join-Path $taskRoot 'publishing/release-notes.md') -Destination $taskPackage
Copy-Item -LiteralPath (Join-Path $taskRoot 'publishing/README.md') -Destination (Join-Path $taskPackage 'PUBLISHING.md')
Copy-Item -LiteralPath (Join-Path $taskRoot 'LICENSE'),(Join-Path $taskRoot 'AI_DISCLOSURE.md'),(Join-Path $taskRoot 'THIRD_PARTY_NOTICES.md') -Destination $taskPackage
Copy-Item -LiteralPath (Join-Path $taskRoot 'LICENSES') -Destination $taskPackage -Recurse
Copy-Item -LiteralPath (Join-Path $taskRoot 'assets') -Destination $taskPackage -Recurse
Copy-Item -LiteralPath (Join-Path $taskRoot 'assets/branding/icon-256.png') -Destination $taskPackage

$taskProjectFile = Join-Path $taskPackage 'modrinth/project.json'
$taskProject = Get-Content -LiteralPath $taskProjectFile -Raw -Encoding UTF8 | ConvertFrom-Json
$taskProject.body = [IO.File]::ReadAllText((Join-Path $taskPackage 'modrinth/description.md'))
$taskProject | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $taskProjectFile -Encoding UTF8
$taskVersionFile = Join-Path $taskPackage 'modrinth/version.json'
$taskVersionData = Get-Content -LiteralPath $taskVersionFile -Raw -Encoding UTF8 | ConvertFrom-Json
$taskVersionData.version_number = $taskVersion
$taskVersionData.name = "Find Me Clientside $taskVersion - Forge $taskMinecraft"
$taskVersionData.game_versions = @($taskMinecraft)
$taskVersionData.changelog = [IO.File]::ReadAllText((Join-Path $taskPackage 'release-notes.md'))
$taskVersionData | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $taskVersionFile -Encoding UTF8

$taskHash = (Get-FileHash -LiteralPath $taskJarPath -Algorithm SHA256).Hash.ToLowerInvariant()
"$taskHash  $taskFileName" | Set-Content -LiteralPath (Join-Path $taskPackage 'SHA256SUMS.txt') -Encoding ASCII
$taskZip = Join-Path $taskDist "findmeclientside-$taskMinecraft-$taskVersion-publishing.zip"
if (Test-Path -LiteralPath $taskZip) { Remove-Item -LiteralPath $taskZip -Force }
[IO.Compression.ZipFile]::CreateFromDirectory($taskPackage, $taskZip)
$taskZipHash = (Get-FileHash -LiteralPath $taskZip -Algorithm SHA256).Hash.ToLowerInvariant()
@("$taskHash  $taskFileName", "$taskZipHash  $(Split-Path -Leaf $taskZip)") |
    Set-Content -LiteralPath (Join-Path $taskDist 'SHA256SUMS.txt') -Encoding ASCII
Write-Output "Mod JAR: $taskJarPath"
Write-Output "Publishing ZIP: $taskZip"
