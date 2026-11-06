# Regenerate earth_night.png (R3.2a asset, NASA SVS 30003).
param([string]$Source, [string]$Out)
Add-Type -AssemblyName System.Drawing
$bi = [System.Drawing.Bitmap]::FromFile($Source)
$target = New-Object System.Drawing.Bitmap 2048, 1024
$g = [System.Drawing.Graphics]::FromImage($target)
$g.DrawImage($bi, 0, 0, 2048, 1024)
$target.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bi.Dispose(); $target.Dispose()
Write-Host "ok: $Out"
Write-Host "source sha256 expected: BFC05C5DE429E52DE8F6AB92FE0022DF2041F648A6983CB4FEDE68CCF05CE795"
Write-Host "target dims 2048x1024"

# --- verify exact output ---
$expected = "8B29D841B4875CECF49402991A8D9E2A4C0DE80B26E70B9331894C0D9523A8B4"
$actual = (Get-FileHash -Algorithm SHA256 $Out).Hash
if ($actual -ne $expected) { throw "SHA mismatch: $actual" }
Write-Host "sha256 ok: $actual"
