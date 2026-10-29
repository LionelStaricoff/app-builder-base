#requires -Version 5.1
<#
.SYNOPSIS
  Ejecuta Maven con el JDK 25 que exige el pom.

.DESCRIPTION
  `pom.xml` declara java.version=25, pero el JAVA_HOME de la máquina puede
  apuntar a otra versión (p. ej. 21) y entonces `mvnw compile` falla con
  "release version 25 not supported". Este wrapper resuelve un JDK 25 antes de
  invocar el wrapper de Maven, sin modificar variables globales.

.EXAMPLE
  .\scripts\dev.ps1 test

.EXAMPLE
  .\scripts\dev.ps1 spring-boot:run -Dspring-boot.run.profiles=local
#>
$ErrorActionPreference = 'Stop'

function Resolve-Jdk25 {
  $candidates = @()

  if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }

  $roots = @(
    (Join-Path $env:ProgramFiles 'Java'),
    (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
    (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium')
  )

  foreach ($root in $roots) {
    if (-not (Test-Path -LiteralPath $root)) { continue }
    $candidates += Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -match '25' } |
      Sort-Object Name -Descending |
      ForEach-Object { $_.FullName }
  }

  foreach ($jdkHome in $candidates) {
    # Leer el archivo `release` evita ejecutar `java -version` (que escribe en
    # stderr y ensucia la salida con ErrorRecords de PowerShell 5.1).
    $releaseFile = Join-Path $jdkHome 'release'
    if (-not (Test-Path -LiteralPath $releaseFile)) { continue }
    $release = Get-Content -LiteralPath $releaseFile -Raw
    if ($release -match 'JAVA_VERSION="25') { return $jdkHome }
  }

  return $null
}

$jdk = Resolve-Jdk25
if (-not $jdk) {
  throw 'No se encontró un JDK 25. Instalá JDK 25 o definí JAVA_HOME apuntando a uno.'
}

$env:JAVA_HOME = $jdk
$env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH

Write-Host "[dev] JDK 25: $jdk"

# Maven y la JVM escriben warnings en stderr (p. ej. Lombok + Unsafe en Java 25).
# Con ErrorActionPreference=Stop, PowerShell 5.1 los convertiría en excepciones
# y abortaría el build, así que se relaja SOLO para la invocación.
$ErrorActionPreference = 'Continue'

$mvnw = Join-Path $PSScriptRoot '..\mvnw.cmd'
& $mvnw @args
exit $LASTEXITCODE
