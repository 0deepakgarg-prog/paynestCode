param(
    [string]$InputMarkdown = "docs/PAYNEST_BUSINESS_API_DOCUMENTATION.md",
    [string]$OutputDocx = "docs/PAYNEST_BUSINESS_API_DOCUMENTATION.docx",
    [string]$Title = "PayNest Wallet APIs - Business Integration Specification"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Escape-XmlText {
    param([AllowNull()][string]$Text)
    if ($null -eq $Text) { return "" }
    return [System.Security.SecurityElement]::Escape($Text)
}

function New-RunXml {
    param(
        [AllowNull()][string]$Text,
        [switch]$Bold,
        [switch]$Italic,
        [switch]$Code
    )

    $props = ""
    if ($Bold -or $Italic -or $Code) {
        $parts = @()
        if ($Bold) { $parts += "<w:b/>" }
        if ($Italic) { $parts += "<w:i/>" }
        if ($Code) { $parts += '<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/>' }
        $props = "<w:rPr>$($parts -join '')</w:rPr>"
    }

    $escaped = Escape-XmlText $Text
    return "<w:r>$props<w:t xml:space=""preserve"">$escaped</w:t></w:r>"
}

function Convert-InlineMarkdownToRuns {
    param([AllowNull()][string]$Text)

    if ([string]::IsNullOrEmpty($Text)) { return "" }

    $runs = New-Object System.Text.StringBuilder
    $pattern = '(`[^`]+`|\*\*[^*]+\*\*)'
    $matches = [regex]::Matches($Text, $pattern)
    $pos = 0

    foreach ($match in $matches) {
        if ($match.Index -gt $pos) {
            [void]$runs.Append((New-RunXml -Text $Text.Substring($pos, $match.Index - $pos)))
        }

        $value = $match.Value
        if ($value.StartsWith('`') -and $value.EndsWith('`')) {
            [void]$runs.Append((New-RunXml -Text $value.Substring(1, $value.Length - 2) -Code))
        } elseif ($value.StartsWith("**") -and $value.EndsWith("**")) {
            [void]$runs.Append((New-RunXml -Text $value.Substring(2, $value.Length - 4) -Bold))
        }
        $pos = $match.Index + $match.Length
    }

    if ($pos -lt $Text.Length) {
        [void]$runs.Append((New-RunXml -Text $Text.Substring($pos)))
    }

    return $runs.ToString()
}

function New-ParagraphXml {
    param(
        [AllowNull()][string]$Text,
        [string]$Style = "Normal"
    )

    $styleXml = if ($Style) { "<w:pPr><w:pStyle w:val=""$Style""/></w:pPr>" } else { "" }
    $runs = Convert-InlineMarkdownToRuns $Text
    return "<w:p>$styleXml$runs</w:p>"
}

function New-CodeBlockXml {
    param([string[]]$Lines)

    $runs = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        [void]$runs.Append((New-RunXml -Text $Lines[$i] -Code))
        if ($i -lt ($Lines.Count - 1)) {
            [void]$runs.Append("<w:r><w:br/></w:r>")
        }
    }

    return @"
<w:p>
  <w:pPr>
    <w:pStyle w:val="CodeBlock"/>
  </w:pPr>
  $($runs.ToString())
</w:p>
"@
}

function Split-MarkdownTableRow {
    param([string]$Line)

    $trimmed = $Line.Trim()
    if ($trimmed.StartsWith("|")) { $trimmed = $trimmed.Substring(1) }
    if ($trimmed.EndsWith("|")) { $trimmed = $trimmed.Substring(0, $trimmed.Length - 1) }
    $values = @($trimmed -split '\|' | ForEach-Object { $_.Trim() })
    return ,$values
}

function Test-MarkdownSeparatorRow {
    param([string]$Line)
    $cells = Split-MarkdownTableRow $Line
    if ($cells.Count -eq 0) { return $false }
    foreach ($cell in $cells) {
        if ($cell -notmatch '^:?-{3,}:?$') { return $false }
    }
    return $true
}

function New-TableXml {
    param([object[]]$Rows)

    if ($Rows.Count -eq 0) { return "" }
    $xml = New-Object System.Text.StringBuilder
    [void]$xml.Append(@"
<w:tbl>
  <w:tblPr>
    <w:tblStyle w:val="TableGrid"/>
    <w:tblW w:w="0" w:type="auto"/>
    <w:tblLook w:val="04A0"/>
  </w:tblPr>
  <w:tblGrid>
"@)

    $maxCells = ($Rows | ForEach-Object { $_.Count } | Measure-Object -Maximum).Maximum
    for ($i = 0; $i -lt $maxCells; $i++) {
        [void]$xml.Append('<w:gridCol w:w="2400"/>')
    }
    [void]$xml.Append("</w:tblGrid>")

    for ($r = 0; $r -lt $Rows.Count; $r++) {
        [void]$xml.Append("<w:tr>")
        foreach ($cell in $Rows[$r]) {
            $shade = if ($r -eq 0) { '<w:shd w:fill="D9EAF7"/>' } else { "" }
            $cellRuns = Convert-InlineMarkdownToRuns $cell
            [void]$xml.Append(@"
<w:tc>
  <w:tcPr><w:tcW w:w="2400" w:type="dxa"/>$shade</w:tcPr>
  <w:p><w:pPr><w:pStyle w:val="TableText"/></w:pPr>$cellRuns</w:p>
</w:tc>
"@)
        }
        [void]$xml.Append("</w:tr>")
    }

    [void]$xml.Append("</w:tbl>")
    return $xml.ToString()
}

function Convert-MarkdownToBodyXml {
    param([string[]]$Lines)

    $body = New-Object System.Text.StringBuilder
    $i = 0
    while ($i -lt $Lines.Count) {
        $line = $Lines[$i]
        $trim = $line.Trim()

        if ([string]::IsNullOrWhiteSpace($line)) {
            $i++
            continue
        }

        if ($trim.StartsWith('```')) {
            $codeLines = New-Object System.Collections.Generic.List[string]
            $i++
            while ($i -lt $Lines.Count -and -not $Lines[$i].Trim().StartsWith('```')) {
                $codeLines.Add($Lines[$i])
                $i++
            }
            if ($i -lt $Lines.Count) { $i++ }
            [void]$body.Append((New-CodeBlockXml -Lines $codeLines.ToArray()))
            continue
        }

        if ($trim.StartsWith("|") -and ($i + 1) -lt $Lines.Count -and (Test-MarkdownSeparatorRow $Lines[$i + 1])) {
            $rows = New-Object System.Collections.Generic.List[object]
            $rows.Add((Split-MarkdownTableRow $Lines[$i]))
            $i += 2
            while ($i -lt $Lines.Count -and $Lines[$i].Trim().StartsWith("|")) {
                $rows.Add((Split-MarkdownTableRow $Lines[$i]))
                $i++
            }
            [void]$body.Append((New-TableXml -Rows $rows.ToArray()))
            continue
        }

        if ($trim -match '^#\s+(.+)$') {
            [void]$body.Append((New-ParagraphXml -Text $Matches[1].Trim() -Style "Title"))
            $i++
            continue
        }

        if ($trim -match '^##\s+(.+)$') {
            [void]$body.Append((New-ParagraphXml -Text $Matches[1].Trim() -Style "Heading1"))
            $i++
            continue
        }

        if ($trim -match '^###\s+(.+)$') {
            [void]$body.Append((New-ParagraphXml -Text $Matches[1].Trim() -Style "Heading2"))
            $i++
            continue
        }

        if ($trim -match '^####\s+(.+)$') {
            [void]$body.Append((New-ParagraphXml -Text $Matches[1].Trim() -Style "Heading3"))
            $i++
            continue
        }

        if ($trim -match '^[-*]\s+(.+)$') {
            [void]$body.Append((New-ParagraphXml -Text ("- " + $Matches[1].Trim()) -Style "ListParagraph"))
            $i++
            continue
        }

        if ($trim -match '^\d+\.\s+(.+)$') {
            [void]$body.Append((New-ParagraphXml -Text $trim -Style "ListParagraph"))
            $i++
            continue
        }

        [void]$body.Append((New-ParagraphXml -Text $trim -Style "Normal"))
        $i++
    }

    return $body.ToString()
}

function Add-ZipEntry {
    param(
        [System.IO.Compression.ZipArchive]$Zip,
        [string]$Name,
        [string]$Content
    )

    $entry = $Zip.CreateEntry($Name)
    $stream = $entry.Open()
    $writer = New-Object System.IO.StreamWriter($stream, [System.Text.UTF8Encoding]::new($false))
    try {
        $writer.Write($Content)
    } finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

$inputPath = Resolve-Path $InputMarkdown
$outputPath = Join-Path (Get-Location) $OutputDocx
$outputDir = Split-Path -Parent $outputPath
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$lines = Get-Content -LiteralPath $inputPath
$bodyXml = Convert-MarkdownToBodyXml -Lines $lines
$now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

$documentXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas"
            xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
            xmlns:o="urn:schemas-microsoft-com:office:office"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
            xmlns:v="urn:schemas-microsoft-com:vml"
            xmlns:wp14="http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:w10="urn:schemas-microsoft-com:office:word"
            xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml"
            xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"
            xmlns:wpi="http://schemas.microsoft.com/office/word/2010/wordprocessingInk"
            xmlns:wne="http://schemas.microsoft.com/office/word/2006/wordml"
            xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
            mc:Ignorable="w14 wp14">
  <w:body>
    $bodyXml
    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="720" w:footer="720" w:gutter="0"/>
      <w:cols w:space="720"/>
      <w:docGrid w:linePitch="360"/>
    </w:sectPr>
  </w:body>
</w:document>
"@

$stylesXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
        <w:sz w:val="22"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
    <w:pPr><w:spacing w:after="120"/></w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Title">
    <w:name w:val="Title"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:pPr><w:jc w:val="center"/><w:spacing w:after="240"/></w:pPr>
    <w:rPr><w:b/><w:color w:val="1F4E79"/><w:sz w:val="36"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:pPr><w:keepNext/><w:spacing w:before="360" w:after="160"/></w:pPr>
    <w:rPr><w:b/><w:color w:val="1F4E79"/><w:sz w:val="30"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:pPr><w:keepNext/><w:spacing w:before="240" w:after="120"/></w:pPr>
    <w:rPr><w:b/><w:color w:val="2F5496"/><w:sz w:val="26"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading3">
    <w:name w:val="heading 3"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:rPr><w:b/><w:color w:val="2F5496"/><w:sz w:val="24"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="CodeBlock">
    <w:name w:val="CodeBlock"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr>
      <w:spacing w:before="80" w:after="160"/>
      <w:ind w:left="240"/>
      <w:shd w:fill="F2F2F2"/>
      <w:pBdr>
        <w:left w:val="single" w:sz="8" w:space="8" w:color="A6A6A6"/>
      </w:pBdr>
    </w:pPr>
    <w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/><w:sz w:val="18"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="ListParagraph">
    <w:name w:val="List Paragraph"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:ind w:left="360"/></w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="TableText">
    <w:name w:val="Table Text"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:spacing w:after="0"/></w:pPr>
    <w:rPr><w:sz w:val="18"/></w:rPr>
  </w:style>
  <w:style w:type="table" w:styleId="TableGrid">
    <w:name w:val="Table Grid"/>
    <w:tblPr>
      <w:tblBorders>
        <w:top w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/>
        <w:left w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/>
        <w:bottom w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/>
        <w:right w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/>
        <w:insideH w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/>
        <w:insideV w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/>
      </w:tblBorders>
      <w:tblCellMar>
        <w:top w:w="80" w:type="dxa"/>
        <w:left w:w="80" w:type="dxa"/>
        <w:bottom w:w="80" w:type="dxa"/>
        <w:right w:w="80" w:type="dxa"/>
      </w:tblCellMar>
    </w:tblPr>
  </w:style>
</w:styles>
"@

$contentTypesXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>
"@

$relsXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>
"@

$documentRelsXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>
"@

$coreXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                   xmlns:dc="http://purl.org/dc/elements/1.1/"
                   xmlns:dcterms="http://purl.org/dc/terms/"
                   xmlns:dcmitype="http://purl.org/dc/dcmitype/"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>$(Escape-XmlText $Title)</dc:title>
  <dc:creator>Codex</dc:creator>
  <cp:lastModifiedBy>Codex</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$now</dcterms:modified>
</cp:coreProperties>
"@

$appXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
            xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>PayNest Documentation Generator</Application>
  <DocSecurity>0</DocSecurity>
  <ScaleCrop>false</ScaleCrop>
  <Company>PayNest</Company>
  <LinksUpToDate>false</LinksUpToDate>
  <SharedDoc>false</SharedDoc>
  <HyperlinksChanged>false</HyperlinksChanged>
  <AppVersion>1.0</AppVersion>
</Properties>
"@

if (Test-Path $outputPath) {
    Remove-Item -LiteralPath $outputPath -Force
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($outputPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    Add-ZipEntry -Zip $zip -Name "[Content_Types].xml" -Content $contentTypesXml
    Add-ZipEntry -Zip $zip -Name "_rels/.rels" -Content $relsXml
    Add-ZipEntry -Zip $zip -Name "word/document.xml" -Content $documentXml
    Add-ZipEntry -Zip $zip -Name "word/styles.xml" -Content $stylesXml
    Add-ZipEntry -Zip $zip -Name "word/_rels/document.xml.rels" -Content $documentRelsXml
    Add-ZipEntry -Zip $zip -Name "docProps/core.xml" -Content $coreXml
    Add-ZipEntry -Zip $zip -Name "docProps/app.xml" -Content $appXml
} finally {
    $zip.Dispose()
}

Get-Item -LiteralPath $outputPath | Select-Object FullName, Length, LastWriteTime
