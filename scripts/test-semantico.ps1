$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & .\mvnw.cmd test-compile
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion de pruebas.' }
    & java -cp 'target/classes;target/test-classes' fisio.semantic.SemanticRegression --gui
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la regresion semantica.' }
} finally {
    Pop-Location
}
