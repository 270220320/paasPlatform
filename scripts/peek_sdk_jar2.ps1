Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead("d:\AiFlow\backend\libs\flight-hub-sdk.jar")
$targets = @("com/dji/flighthubsdk/generated/IFlightTaskModule.class", "com/dji/flighthubsdk/core/FlightTaskModule.class")
foreach ($name in $targets) {
  $entry = $zip.Entries | Where-Object { $_.FullName -eq $name } | Select-Object -First 1
  if (-not $entry) { Write-Output "$name NOT FOUND"; continue }
  $ms = New-Object System.IO.MemoryStream
  $s = $entry.Open(); $s.CopyTo($ms); $s.Close()
  $bytes = $ms.ToArray()
  $text = [System.Text.Encoding]::ASCII.GetString($bytes)
  Write-Output "=== $name ==="
  [regex]::Matches($text, '[a-zA-Z][a-zA-Z0-9_]{5,60}') | ForEach-Object { $_.Value } | Sort-Object -Unique | Where-Object { $_ -match 'execute|cancel|create|delete|edit|detail|update' } | ForEach-Object { Write-Output $_ }
}
$zip.Dispose()
