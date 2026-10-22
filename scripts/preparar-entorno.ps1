param([switch]$Force)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$EnvPath = Join-Path $Root '.env'
if ((Test-Path $EnvPath) -and -not $Force) {
    Write-Error 'Ya existe .env. No se sobrescribió. Usa -Force solo si deseas generar nuevas credenciales.'
}
function New-UrlSafeSecret([int]$Bytes = 36) {
    $Buffer = New-Object byte[] $Bytes
    $Rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $Rng.GetBytes($Buffer) } finally { $Rng.Dispose() }
    return [Convert]::ToBase64String($Buffer).TrimEnd('=').Replace('+','-').Replace('/','_')
}
$DockerHub = Read-Host 'Usuario de Docker Hub (Enter para usar local)'
if ([string]::IsNullOrWhiteSpace($DockerHub)) { $DockerHub = 'local' }
$Lines = @(
    '# Generado localmente por scripts/preparar-entorno.ps1. No subir a Git.',
    "DOCKERHUB_USER=$DockerHub",
    'TAG=latest',
    'HTTP_PORT=80',
    'POSTGRES_DB=alexrifas',
    'POSTGRES_USER=alexrifas',
    "POSTGRES_PASSWORD=$(New-UrlSafeSecret 30)",
    "REDIS_PASSWORD=$(New-UrlSafeSecret 30)",
    'MONGO_USER=alexrifas',
    "MONGO_PASSWORD=$(New-UrlSafeSecret 30)",
    "JWT_SECRET=$(New-UrlSafeSecret 48)",
    'ADMIN_CORREO=admin@alexrifas.local',
    "ADMIN_CONTRASENA=$(New-UrlSafeSecret 30)"
)
[System.IO.File]::WriteAllLines($EnvPath, $Lines, [System.Text.UTF8Encoding]::new($false))
Write-Host "Creado: $EnvPath" -ForegroundColor Green
Write-Host 'Guarda estas credenciales fuera del repositorio. ADMIN_CONTRASENA queda en .env.' -ForegroundColor Yellow
Write-Host 'Para publicar en Docker Hub, revisa DOCKERHUB_USER y luego ejecuta docker login.'
