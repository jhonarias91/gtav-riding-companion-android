# Regenerates mipmap-*dpi/ic_launcher_bitmap.png and ic_launcher_bitmap_round.png from the adaptive icon vectors.
# Needs only Windows' built-in .NET Framework compiler. Run from anywhere:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\launcher-icons\Render-LauncherIcons.ps1
$ErrorActionPreference = 'Stop'
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$out = Join-Path ([IO.Path]::GetTempPath()) ('launcher-icons-' + [Guid]::NewGuid().ToString('N') + '.exe')
try {
    & $compiler /nologo /target:exe "/out:$out" /reference:System.Drawing.dll /reference:System.Xml.Linq.dll /reference:System.Xml.dll /reference:System.Core.dll (Join-Path $PSScriptRoot 'LauncherIcons.cs')
    if ($LASTEXITCODE -ne 0) { throw 'LauncherIcons.cs did not compile.' }
    & $out (Resolve-Path (Join-Path $PSScriptRoot '..\..\app\src\main\res')).Path
    if ($LASTEXITCODE -ne 0) { throw 'Rendering failed.' }
} finally {
    if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out }
}
