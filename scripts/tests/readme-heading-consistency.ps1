[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = (& git -C $PSScriptRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Unable to locate repository from $PSScriptRoot"
}

$translations = @{
    "目录" = @("Table of Contents", "目次")
    "释义" = @("Meaning", "意味")
    "介绍" = @("Introduction", "はじめに")
    "支持范围" = @("Supported Versions", "対応範囲")
    "特性" = @("Features", "特徴")
    "配置说明" = @("Configuration", "設定")
    "通用部分" = @("Shared Files", "共通ファイル")
    "模组部分" = @("Mod Files", "モジュールファイル")
    "服务端配置要点（垃圾箱相关）" = @("Server Configuration Highlights (Dustbin)", "サーバー設定の要点（ゴミ箱関連）")
    "作为依赖接入" = @("Using Banira as a Dependency", "依存 MOD としての導入")
    "加载器元数据" = @("Loader Metadata", "ローダーメタデータ")
    "稳定公共 API" = @("Stable Public API", "安定した公開 API")
    "事件与生命周期" = @("Events and Lifecycle", "イベントとライフサイクル")
    "网络" = @("Networking", "ネットワーク")
    "客户端能力" = @("Client Features", "クライアント機能")
    "数据与路径" = @("Data and Paths", "データとパス")
    "版本迁移原则" = @("Version Migration", "バージョン移行")
    "指令说明" = @("Commands", "コマンド")
    "奖励系统" = @("Reward System", "報酬システム")
    "奖励规则" = @("Reward Rules", "報酬ルール")
    "奖励类型" = @("Reward Types", "報酬タイプ")
    "历法与个性化日期" = @("Calendars and Personal Dates", "暦とパーソナル日付")
    "抽奖奖池" = @("Lottery Pools", "抽選プール")
    "数据与迁移" = @("Data and Migration", "データと移行")
    "实体过滤器" = @("Entity Filter", "エンティティフィルター")
    "例子" = @("Examples", "例")
    "说明" = @("Notes", "説明")
    "注意事项" = @("Notes", "注意事項")
    "性能测试" = @("Performance Tests", "性能テスト")
    "构建" = @("Building", "ビルド")
    "许可证" = @("License", "ライセンス")
    "TODO" = @("TODO", "TODO")
    "Forge" = @("Forge", "Forge")
    "Fabric" = @("Fabric", "Fabric")
    "NeoForge" = @("NeoForge", "NeoForge")
    "Maven Local" = @("Maven Local", "Maven Local")
}

function Get-Headings {
    param([string]$Path)

    return @(Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        if ($_ -match "^(#{2,3})\s+(.+?)\s*$") {
            [PSCustomObject]@{
                Level = $matches[1].Length
                Text = $matches[2]
            }
        }
    })
}

function Get-TocEntries {
    param([string]$Path)

    return @(Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        if ($_ -match "^\s{2,}-\s+\[([^\]]+)\]\(#([^)]+)\)\s*$") {
            [PSCustomObject]@{
                Text = $matches[1]
                Anchor = $matches[2]
            }
        }
    })
}

function ConvertTo-MarkdownAnchor {
    param([string]$Heading)

    $anchor = $Heading.ToLowerInvariant()
    $anchor = [regex]::Replace($anchor, "[^\p{L}\p{Nd}\s-]", "")
    $anchor = [regex]::Replace($anchor.Trim(), "\s+", "-")
    return [regex]::Replace($anchor, "-{2,}", "-")
}

function Assert-TocMatchesHeadings {
    param([string]$Path)

    $headings = @(Get-Headings $Path | Where-Object { $_.Level -eq 2 -and $_.Text -ne "TODO" })
    $entries = Get-TocEntries $Path
    if ($headings.Count -ne $entries.Count) {
        throw "TOC entry count differs from H2 headings in ${Path}: toc=$($entries.Count), headings=$($headings.Count)"
    }
    for ($index = 0; $index -lt $headings.Count; $index++) {
        $heading = $headings[$index].Text
        $entry = $entries[$index]
        $expectedAnchor = ConvertTo-MarkdownAnchor $heading
        if ($entry.Text -ne $heading -or $entry.Anchor -ne $expectedAnchor) {
            throw "TOC mismatch in ${Path}: expected [$heading](#$expectedAnchor), got [$($entry.Text)](#$($entry.Anchor))"
        }
    }
}

$zhHeadings = Get-Headings (Join-Path $repoRoot "README.md")
$enHeadings = Get-Headings (Join-Path $repoRoot "locales\README_en.md")
$jaHeadings = Get-Headings (Join-Path $repoRoot "locales\README_ja.md")

if ($zhHeadings.Count -ne $enHeadings.Count -or $zhHeadings.Count -ne $jaHeadings.Count) {
    throw "Heading counts differ: zh=$($zhHeadings.Count), en=$($enHeadings.Count), ja=$($jaHeadings.Count)"
}

for ($index = 0; $index -lt $zhHeadings.Count; $index++) {
    $zh = $zhHeadings[$index]
    $en = $enHeadings[$index]
    $ja = $jaHeadings[$index]
    if (-not $translations.ContainsKey($zh.Text)) {
        throw "Missing canonical translation for heading: $($zh.Text)"
    }
    if ($zh.Level -ne $en.Level -or $zh.Level -ne $ja.Level) {
        throw "Heading level differs for $($zh.Text): zh=$($zh.Level), en=$($en.Level), ja=$($ja.Level)"
    }
    $expected = $translations[$zh.Text]
    if ($en.Text -ne $expected[0] -or $ja.Text -ne $expected[1]) {
        throw "$($zh.Text) expected [$($expected[0]) | $($expected[1])], got [$($en.Text) | $($ja.Text)]"
    }
}

foreach ($path in @(
    (Join-Path $repoRoot "README.md"),
    (Join-Path $repoRoot "locales\README_en.md"),
    (Join-Path $repoRoot "locales\README_ja.md")
)) {
    Assert-TocMatchesHeadings $path
    $text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    if ($text -cmatch "Neoforge") {
        throw "Incorrect NeoForge spelling in $path"
    }
    if ($text -match "Forge.{0,40}NeoForge.{0,40}Fabric") {
        throw "Loader order must be Forge, Fabric, NeoForge in $path"
    }
}

Write-Host "PASS: README heading terminology"
