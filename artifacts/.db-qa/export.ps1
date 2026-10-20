$ErrorActionPreference='Stop'
$docPath='D:\AiFlow\artifacts\上云2基座数据库用途说明.docx'
$pdfPath='D:\AiFlow\artifacts\.db-qa\database.pdf'
$word=New-Object -ComObject kwps.Application
$word.Visible=$false
$word.DisplayAlerts=0
$document=$word.Documents.Open($docPath,$false,$true)
try {
    $document.ExportAsFixedFormat($pdfPath,17)
    Write-Output "PDF export completed"
} finally {
    $document.Close($false)
    $word.Quit()
}
