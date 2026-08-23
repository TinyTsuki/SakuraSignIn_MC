[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$buildScript = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\build-all.ps1"))
$repoRoot = (& git -C $PSScriptRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Unable to locate repository from $PSScriptRoot"
}

function Get-ListedBranches {
    param([string[]]$Expression)

    if ($null -eq $Expression) {
        $output = @(& $buildScript -ListOnly 6>&1)
    } else {
        $output = @(& $buildScript -ListOnly -BranchExpression $Expression 6>&1)
    }
    return @($output | ForEach-Object {
        if ($_ -match "^\[([^\]]+)\]\s") {
            $matches[1]
        }
    })
}

function Get-ListedOutput {
    param([string[]]$Expression)

    return @(& $buildScript -ListOnly -BranchExpression $Expression 6>&1 | ForEach-Object { "$_" })
}

function Assert-Branches {
    param(
        [string]$Case,
        [string[]]$Expected,
        [string[]]$Actual
    )

    $expectedText = $Expected -join ","
    $actualText = $Actual -join ","
    if ($expectedText -ne $actualText) {
        throw "$Case expected [$expectedText], got [$actualText]"
    }
}

$localBranches = @(& git -C $repoRoot for-each-ref "--format=%(refname:short)" refs/heads)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to list local branches"
}
$defaultBranches = @("forge", "fabric", "neoforge") | ForEach-Object {
    $prefix = "$_/"
    $localBranches | Where-Object { $_.StartsWith($prefix) } | Sort-Object
}

Assert-Branches "default loader namespaces" $defaultBranches (Get-ListedBranches)
Assert-Branches "cross-loader version wildcard" @(
    "fabric/21.1",
    "forge/21.1",
    "neoforge/21.1"
) (Get-ListedBranches @("*/21.1"))
Assert-Branches "include then exclude" @(
    "forge/18.2",
    "forge/19.2",
    "forge/20.1",
    "forge/21.1"
) (Get-ListedBranches @("forge/*", "!forge/16.5"))
Assert-Branches "literal branch" @("fabric/18.2") (Get-ListedBranches @("fabric/18.2"))

$fabric16Line = Get-ListedOutput @("fabric/16.5") |
        Where-Object { $_ -match "^\[fabric/16\.5\]\s" } |
        Select-Object -First 1
if ($fabric16Line -notmatch "Target Java 8 \(.+\), Gradle 9\.2\.1 on Java 21 \(.+\)") {
    throw "Fabric 1.16.5 must use Java 21 to run Gradle and Loom while retaining the Java 8 target: $fabric16Line"
}

$buildScriptSource = Get-Content -LiteralPath $buildScript -Raw -Encoding UTF8
foreach ($requiredHelper in @(
        "Test-WorktreeRegistered",
        "Remove-DirectoryWithRetry",
        "Remove-TemporaryWorktree",
        "Export-BuildArtifacts"
)) {
    if ($buildScriptSource -notmatch [regex]::Escape("function $requiredHelper")) {
        throw "Build script is missing cleanup helper: $requiredHelper"
    }
}

Write-Host "PASS: build branch expression selection"
