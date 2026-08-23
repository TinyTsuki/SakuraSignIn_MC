[CmdletBinding()]
param(
    [switch]$PublishToMavenLocal,
    [switch]$ListOnly,
    [string]$LocalInputsRoot,
    [Alias("Branches")]
    [string[]]$BranchExpression = @(
        "forge/*",
        "fabric/*",
        "neoforge/*"
    )
)

$ErrorActionPreference = "Stop"
$docsRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$repoRoot = (& git -C $docsRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
    throw "Unable to locate the repository from $docsRoot"
}

function Assert-NativeSuccess {
    param(
        [string]$Description,
        [int]$ExitCode
    )

    if ($exitCode -ne 0) {
        throw "$Description failed with exit code $ExitCode"
    }
}

function Test-WorktreeRegistered {
    param([string]$Path)

    $expected = [IO.Path]::GetFullPath($Path)
    foreach ($line in @(& git -C $repoRoot worktree list --porcelain)) {
        if ($line -match "^worktree\s+(.+)$" -and
                [IO.Path]::GetFullPath($matches[1]) -eq $expected) {
            return $true
        }
    }
    return $false
}

function Remove-DirectoryWithRetry {
    param([string]$Path)

    $fullPath = [IO.Path]::GetFullPath($Path)
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        if (-not (Test-Path -LiteralPath $fullPath)) {
            return
        }
        try {
            [IO.Directory]::Delete($fullPath, $true)
            return
        } catch {
            if ($attempt -eq 3) {
                throw
            }
            Start-Sleep -Seconds 2
        }
    }
}

function Remove-TemporaryWorktree {
    param(
        [string]$Branch,
        [string]$Path
    )

    $lastOutput = @()
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $previousErrorAction = $ErrorActionPreference
        try {
            $ErrorActionPreference = "Continue"
            $lastOutput = @(& git -C $repoRoot worktree remove --force -- $Path 2>&1)
            $removeExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorAction
        }
        if ($removeExitCode -eq 0 -or -not (Test-WorktreeRegistered $Path)) {
            Remove-DirectoryWithRetry $Path
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "git worktree remove $branch failed: $($lastOutput -join [Environment]::NewLine)"
}

function Export-BuildArtifacts {
    param(
        [string]$Branch,
        [string]$WorktreePath,
        [string]$OutputPath
    )

    $sourceRoot = Join-Path $WorktreePath "builds"
    $artifacts = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter "*.jar" -ErrorAction SilentlyContinue)
    if ($artifacts.Count -eq 0) {
        throw "Build produced no distributable jars for $branch under $sourceRoot"
    }

    foreach ($artifact in $artifacts) {
        $relativePath = $artifact.FullName.Substring($sourceRoot.Length).TrimStart("\", "/")
        $destination = Join-Path $OutputPath $relativePath
        New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $artifact.FullName -Destination $destination -Force
        Write-Host "[$branch] Artifact: $destination"
    }
}

function Get-BranchFile {
    param(
        [string]$Branch,
        [string]$Path
    )

    $content = @(& git -C $repoRoot show "${Branch}:${Path}")
    Assert-NativeSuccess "git show ${Branch}:${Path}" $LASTEXITCODE
    return $content -join [Environment]::NewLine
}

function Test-BranchFile {
    param(
        [string]$Branch,
        [string]$Path
    )

    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = "SilentlyContinue"
        & git -C $repoRoot cat-file -e "${Branch}:${Path}" 2>$null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
}

function Get-JavaVersion {
    param([string]$Branch)

    $buildScript = Get-BranchFile $Branch "build.gradle"
    $match = [regex]::Match($buildScript, "(?m)^\s*def\s+javaVer\s*=\s*(\d+)\s*$")
    if (-not $match.Success) {
        $match = [regex]::Match($buildScript, "JavaLanguageVersion\.of\((\d+)\)")
    }
    if (-not $match.Success) {
        throw "Unable to read the Java target from ${Branch}:build.gradle"
    }
    return $match.Groups[1].Value
}

function Get-GradleVersion {
    param([string]$Branch)

    $wrapperProperties = Get-BranchFile $Branch "gradle/wrapper/gradle-wrapper.properties"
    $match = [regex]::Match($wrapperProperties, "gradle-(\d+(?:\.\d+)+)-(?:bin|all)\.zip")
    if (-not $match.Success) {
        throw "Unable to read Gradle version from ${Branch}:gradle/wrapper/gradle-wrapper.properties"
    }
    return [Version]$match.Groups[1].Value
}

function Get-GradleJavaVersion {
    param([Version]$GradleVersion)

    # Current Gradle 8/9 branches also load plugins such as Loom 1.15 that require Java 21.
    if ($GradleVersion.Major -ge 8) {
        return "21"
    }
    if ($GradleVersion.Major -eq 7 -and $GradleVersion.Minor -ge 3) {
        return "17"
    }
    if ($GradleVersion.Major -eq 7) {
        return "16"
    }
    return "8"
}

function Get-JavaHome {
    param(
        [string]$Branch,
        [string]$JavaVersion
    )

    if (Test-BranchFile $Branch "jdks.properties") {
        $jdkProperties = Get-BranchFile $Branch "jdks.properties"
        foreach ($line in $jdkProperties -split "\r?\n") {
            if ($line -match "^\s*jdk$([regex]::Escape($JavaVersion))\s*=\s*(.+?)\s*$") {
                return $matches[1]
            }
        }
    }

    $environmentNames = @(
        "JDK${JavaVersion}_HOME",
        "JAVA${JavaVersion}_HOME",
        "JAVA_HOME_${JavaVersion}",
        "JAVA_HOME"
    )
    $candidates = foreach ($name in $environmentNames) {
        [Environment]::GetEnvironmentVariable($name)
    }
    foreach ($root in @(
        "D:\Programs\Java",
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:ProgramFiles "Eclipse Adoptium")
    )) {
        if (Test-Path -LiteralPath $root -PathType Container) {
            $candidates += Get-ChildItem -LiteralPath $root -Directory | ForEach-Object FullName
        }
    }

    foreach ($candidate in @($candidates | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)) {
        $java = Join-Path $candidate "bin\java.exe"
        if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
            continue
        }
        $processInfo = New-Object System.Diagnostics.ProcessStartInfo
        $processInfo.FileName = $java
        $processInfo.Arguments = "-version"
        $processInfo.UseShellExecute = $false
        $processInfo.RedirectStandardError = $true
        $processInfo.CreateNoWindow = $true
        $process = [System.Diagnostics.Process]::Start($processInfo)
        $versionLine = $process.StandardError.ReadLine()
        $process.WaitForExit()
        $process.Dispose()
        $match = [regex]::Match($versionLine, '"(?:1\.)?(\d+)')
        if ($match.Success -and [int]$match.Groups[1].Value -eq [int]$JavaVersion) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }

    throw "Unable to find a local JDK ${JavaVersion} for $Branch"
}

function Find-LocalInputsRoot {
    if (-not [string]::IsNullOrWhiteSpace($LocalInputsRoot)) {
        $resolved = [IO.Path]::GetFullPath($LocalInputsRoot)
        if (-not (Test-Path -LiteralPath $resolved -PathType Container)) {
            throw "Local inputs root does not exist: $resolved"
        }
        return $resolved
    }

    # Ignored local jars are not copied by git worktree, so reuse them from an existing checkout.
    $worktreeLines = @(& git -C $repoRoot worktree list --porcelain)
    Assert-NativeSuccess "git worktree list" $LASTEXITCODE
    foreach ($line in $worktreeLines) {
        if ($line -match "^worktree\s+(.+)$") {
            $candidate = $matches[1]
            if (Test-Path -LiteralPath (Join-Path $candidate "libs") -PathType Container) {
                return $candidate
            }
        }
    }
    return $null
}

function Select-BuildBranches {
    $expressions = @($BranchExpression | ForEach-Object {
        $_ -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    })
    if ($expressions.Count -eq 0) {
        throw "At least one branch expression is required"
    }

    $allBranches = @(& git -C $repoRoot for-each-ref "--format=%(refname:short)" refs/heads)
    Assert-NativeSuccess "git for-each-ref" $LASTEXITCODE
    $selected = New-Object System.Collections.Generic.List[string]

    foreach ($expression in $expressions | Where-Object { -not $_.StartsWith("!") }) {
        $matches = @($allBranches | Where-Object { $_ -like $expression } | Sort-Object)
        if ($matches.Count -eq 0 -and $expression -notmatch "[*?[]") {
            throw "Branch does not exist: $expression"
        }
        foreach ($branch in $matches) {
            if (-not $selected.Contains($branch)) {
                $selected.Add($branch)
            }
        }
    }

    $exclusions = @($expressions | Where-Object { $_.StartsWith("!") } | ForEach-Object { $_.Substring(1) })
    $result = @($selected | Where-Object {
        $branch = $_
        -not ($exclusions | Where-Object { $branch -like $_ })
    })
    if ($result.Count -eq 0) {
        throw "Branch expressions selected no branches: $($expressions -join ', ')"
    }
    return $result
}

$worktreeBase = Join-Path (Split-Path $repoRoot -Parent) (
    ".docs-build-" + ([IO.Path]::GetFileName($repoRoot) -replace "[^A-Za-z0-9._-]", "_")
)
$localInputs = Find-LocalInputsRoot
$branches = Select-BuildBranches
$tasks = @("clean", "test", "assemble")
if ($PublishToMavenLocal) {
    $tasks += "publishToMavenLocal"
}

Write-Host "Repository: $repoRoot"
Write-Host "Tasks: $($tasks -join ' ')"
Write-Host "Branches: $($Branches -join ', ')"

foreach ($branch in $Branches) {
    $commitOutput = @(& git -C $repoRoot rev-parse --verify "refs/heads/$branch")
    Assert-NativeSuccess "git rev-parse refs/heads/$branch" $LASTEXITCODE
    if ($commitOutput.Count -eq 0) {
        throw "git rev-parse returned no commit for $branch"
    }
    $commit = $commitOutput[0].Trim()
    $targetJavaVersion = Get-JavaVersion $branch
    $targetJavaHome = Get-JavaHome $branch $targetJavaVersion
    $gradleVersion = Get-GradleVersion $branch
    $gradleJavaVersion = Get-GradleJavaVersion $gradleVersion
    $gradleJavaHome = Get-JavaHome $branch $gradleJavaVersion
    Write-Host "[$branch] $commit, Target Java $targetJavaVersion ($targetJavaHome), Gradle $gradleVersion on Java $gradleJavaVersion ($gradleJavaHome)"

    if ($ListOnly) {
        continue
    }

    if (Test-Path -LiteralPath $worktreeBase) {
        if (Test-WorktreeRegistered $worktreeBase) {
            throw "Temporary worktree is already active: $worktreeBase"
        }
        Remove-DirectoryWithRetry $worktreeBase
    }

    $worktreeAdded = $false
    try {
        & git -C $repoRoot worktree add --detach $worktreeBase $commit | Out-Null
        Assert-NativeSuccess "git worktree add $branch" $LASTEXITCODE
        $worktreeAdded = $true

        if ($null -ne $localInputs) {
            Copy-Item -LiteralPath (Join-Path $localInputs "libs") `
                -Destination (Join-Path $worktreeBase "libs") -Recurse -Force
        }

        $previousJavaHome = $env:JAVA_HOME
        $previousPath = $env:Path
        try {
            # 构建工具使用现代 JDK，低版本 Minecraft 仍由目标 toolchain 编译。
            $env:JAVA_HOME = $gradleJavaHome
            $env:Path = "$gradleJavaHome\bin;$previousPath"
            $toolchainPaths = @($targetJavaHome, $gradleJavaHome) | Select-Object -Unique
            $toolchainProperty = "-Dorg.gradle.java.installations.paths=$($toolchainPaths -join ',')"
            Push-Location $worktreeBase
            try {
                & ".\gradlew.bat" @tasks "--no-daemon" "--console=plain" $toolchainProperty
                if ($LASTEXITCODE -ne 0) {
                    throw "Gradle failed for $branch with exit code $LASTEXITCODE"
                }
            } finally {
                Pop-Location
            }
            Export-BuildArtifacts $branch $worktreeBase (Join-Path $repoRoot "builds")
        } finally {
            $env:JAVA_HOME = $previousJavaHome
            $env:Path = $previousPath
        }
    } finally {
        if ($worktreeAdded) {
            Remove-TemporaryWorktree $branch $worktreeBase
        }
        & git -C $repoRoot worktree prune | Out-Null
        Assert-NativeSuccess "git worktree prune" $LASTEXITCODE
    }
}

Write-Host "All requested branches completed."
