# Launch Wavedrom Studio
Write-Host "Compiling Wavedrom Studio with sbt fastLinkJS..." -ForegroundColor Cyan
sbt fastLinkJS
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nCompilation succeeded! Starting local studio server..." -ForegroundColor Green
    node server.js
} else {
    Write-Host "`nCompilation failed!" -ForegroundColor Red
}
