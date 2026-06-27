$path = "app/src/main/java/com/sketcher/sketchercompanionv1/utils/StudioLayout.kt"
$code = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

$openCount = 0
$closeCount = 0
$inString = $false
$inComment = $false
$inLineComment = $false

$bracesStack = @()

for ($i = 0; $i -lt $code.Length; $i++) {
    $char = $code[$i]
    $nextChar = if ($i + 1 -lt $code.Length) { $code[$i+1] } else { $null }
    
    if ($inLineComment) {
        if ($char -eq "`n") { $inLineComment = $false }
        continue
    }
    if ($inComment) {
        if ($char -eq "*" -and $nextChar -eq "/") { $inComment = $false; $i++ }
        continue
    }
    if ($inString) {
        if ($char -eq '"') { $inString = $false }
        continue
    }
    
    if ($char -eq "/" -and $nextChar -eq "/") { $inLineComment = $true; $i++; continue }
    if ($char -eq "/" -and $nextChar -eq "*") { $inComment = $true; $i++; continue }
    if ($char -eq '"') { $inString = $true; continue }
    
    if ($char -eq "{") {
        $openCount++
        $bracesStack += $i
    }
    if ($char -eq "}") {
        $closeCount++
        if ($bracesStack.Length -gt 0) {
            $bracesStack = $bracesStack[0..($bracesStack.Length-2)]
        } else {
            Write-Host "Unmatched closing brace at char index $i"
        }
    }
}

Write-Host "Total Open: $openCount, Total Close: $closeCount"
if ($bracesStack.Length -gt 0) {
    Write-Host "Unmatched open braces at indices: $bracesStack"
    foreach ($idx in $bracesStack) {
        $lineNum = ($code.Substring(0, $idx) -split "`n").Length
        $snippetStart = [math]::Max(0, $idx - 50)
        $snippetLength = [math]::Min($code.Length - $idx, 100)
        $snippet = $code.Substring($snippetStart, $snippetLength)
        Write-Host "Unmatched '{' around line $lineNum : '$snippet'"
    }
}

