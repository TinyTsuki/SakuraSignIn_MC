param(
    [Parameter(Mandatory)][string]$OutputRoot,
    [Parameter(Mandatory)][string]$MavenRoot,
    [Parameter(Mandatory)][string]$EvidenceRoot,
    [string]$Gradle = 'D:/Data/Gradle/wrapper/dists/gradle-8.8-bin/dl7vupf4psengwqhwktix4v1/gradle-8.8/bin/gradle.bat',
    [switch]$DefaultModel
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath("$PSScriptRoot/../..")
$evidence = [IO.Path]::GetFullPath($EvidenceRoot)
[void][IO.Directory]::CreateDirectory($evidence)
$modelPath = Join-Path $evidence 'build-output-manifest.json'
$initPath = Join-Path $evidence 'output-model.init.gradle'
if (Test-Path -LiteralPath $modelPath) { throw 'Output model evidence already exists' }
$init = @'
import groovy.json.JsonOutput
import org.gradle.api.tasks.bundling.AbstractArchiveTask
gradle.projectsEvaluated {
    rootProject.tasks.register('auiOutputModel') {
        doLast {
            def p = rootProject
            def payload = [
                archives: p.tasks.withType(AbstractArchiveTask).collect {
                    [task: it.name, runtimeIntermediate: it.class.superclass.name == 'net.neoforged.gradle.neoform.runtime.tasks.PackJar', directory: it.destinationDirectory.get().asFile.canonicalPath,
                     artifact: it.archiveFile.get().asFile.canonicalPath]
                }.sort { it.task },
                runtimeRoot: p.layout.buildDirectory.dir('neoForm').get().asFile.canonicalPath,
                published: p.publishing.publications.collectMany { publication ->
                    publication.artifacts.collect { [publication: publication.name, artifact: it.file.canonicalPath] }
                },
                hasReobfCopy: p.tasks.findByName('reobfJarJar') != null || p.tasks.findByName('jarJar') != null,
                copyDirectory: p.hasProperty('baniraDistributionDirectory')
                    ? p.baniraDistributionDirectory.canonicalPath : null,
                mavenRoots: p.repositories.findAll { it.name == 'MavenLocal' }.collect { new File(it.url).canonicalPath }
            ]
            def target = new File(System.getProperty('banira.outputModel'))
            target.setText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)), 'UTF-8')
        }
    }
}
'@
[IO.File]::WriteAllText($initPath, $init, [Text.UTF8Encoding]::new($false))
$args = @('-p', $projectRoot, '--offline', '--no-daemon', '--max-workers=1',
    '-Dorg.gradle.jvmargs=-Xmx1024m', '--console=plain', '-I', $initPath,
    "-Dmaven.repo.local=$MavenRoot", "-Dbanira.outputModel=$modelPath", 'auiOutputModel')
if (!$DefaultModel) { $args += "-Pbanira.validationOutputRoot=$OutputRoot" }
& $Gradle @args
if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $modelPath)) { throw 'Gradle output model unavailable' }
$model = Get-Content -LiteralPath $modelPath -Encoding UTF8 -Raw | ConvertFrom-Json
$checks = [Collections.Generic.List[object]]::new()
function Check([string]$Name, [bool]$Passed) { $checks.Add([pscustomobject]@{name=$Name;passed=$Passed}) }
function IsWithin([string]$Path, [string]$Root) {
    $parent = [IO.Path]::GetFullPath($Root).TrimEnd([char[]]@(47,92))
    return [IO.Path]::GetFullPath($Path).StartsWith($parent + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
}
Check 'control:archive-model-present' (@($model.archives).Count -gt 0)
Check 'control:isolated-maven-root' (@($model.mavenRoots).Count -gt 0 -and
    @($model.mavenRoots | Where-Object { [IO.Path]::GetFullPath($_) -ne [IO.Path]::GetFullPath($MavenRoot) }).Count -eq 0)
if (!$DefaultModel) {
    foreach ($archive in $model.archives) {
        $expectedRoot=if($archive.runtimeIntermediate){$model.runtimeRoot}else{$OutputRoot}
        Check ("archive:" + $archive.task) ((IsWithin $archive.artifact $expectedRoot) -and
            $archive.artifact -notmatch '(?i)[\\/]builds[\\/]')
    }
    Check 'publication:present' (@($model.published).Count -eq 2)
    foreach($artifact in $model.published) { Check ('publication:'+$artifact.publication) (IsWithin $artifact.artifact $OutputRoot) }
    if ($model.hasReobfCopy) {
        Check 'reobf-copy:declared' (![string]::IsNullOrWhiteSpace($model.copyDirectory))
        $buildScript = Get-Content -LiteralPath (Join-Path $projectRoot 'build.gradle') -Encoding UTF8 -Raw
        if ($buildScript -match 'doLast\s*\{[\s\S]*?copy\s*\{') {
            Check 'reobf-copy:bound-to-distribution' ($buildScript -match 'into\s+baniraDistributionDirectory' -and
                $buildScript -notmatch 'into\s+file\("\$\{projectDir\}/builds/')
        }
        if ($model.copyDirectory) {
            Check 'reobf-copy:isolated' ((IsWithin (Join-Path $model.copyDirectory 'output.jar') $OutputRoot) -and
                $model.copyDirectory -notmatch '(?i)[\\/]builds[\\/]')
        }
    }
} else {
    Check 'default:model-retains-distribution' (@($model.archives | Where-Object { $_.artifact -match '(?i)[\\/]builds[\\/]' }).Count -gt 0)
}
$report = @{kind='assertions'; utc=[DateTime]::UtcNow.ToString('o'); checks=@($checks.ToArray())}
[IO.File]::WriteAllText((Join-Path $evidence 'assertions.json'), ($report | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
$checks | Format-Table -AutoSize
if (@($checks | Where-Object {!$_.passed}).Count) { exit 1 }
if (!$DefaultModel) {
    $defaultEvidence = Join-Path $evidence 'default-model'
    & $PSCommandPath -OutputRoot $OutputRoot -MavenRoot $MavenRoot -EvidenceRoot $defaultEvidence -Gradle $Gradle -DefaultModel
    if ($LASTEXITCODE -ne 0) { throw 'Default output model failed' }
    $defaultReport = Get-Content -LiteralPath (Join-Path $defaultEvidence 'assertions.json') -Encoding UTF8 -Raw | ConvertFrom-Json
    foreach ($check in $defaultReport.checks) { Check ('default:' + $check.name) $check.passed }
    $report.checks = @($checks.ToArray())
    $report.utc = [DateTime]::UtcNow.ToString('o')
    [IO.File]::WriteAllText((Join-Path $evidence 'assertions.json'), ($report | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
}
