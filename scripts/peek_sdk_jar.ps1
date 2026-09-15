Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead("d:\AiFlow\backend\libs\flight-hub-sdk.jar")
Write-Output "=== Task-related entries ==="
$zip.Entries | Where-Object { $_.FullName -match "bean/.*(Task|task)" } | ForEach-Object { Write-Output $_.FullName }
Write-Output "=== Extract CreateTaskReq ==="
$entry = $zip.Entries | Where-Object { $_.FullName -eq "com/dji/flighthubsdk/bean/CreateTaskReq.class" } | Select-Object -First 1
if ($entry) {
    $dest = "d:\AiFlow\scripts\tmp_class"
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    $out = [System.IO.Stream]::Create((Join-Path $dest "CreateTaskReq.class"))
    $in = $entry.Open()
    $in.CopyTo($out)
    $in.Close(); $out.Close()
    Write-Output "saved to $dest\CreateTaskReq.class ($($entry.Length) bytes)"
} else {
    Write-Output "CreateTaskReq.class NOT FOUND"
}
$zip.Dispose()
