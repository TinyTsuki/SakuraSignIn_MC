<?php
// 获取并过滤输入，不要XSS攻击我😭
$modid = preg_replace('/[^a-zA-Z0-9_-]/', '', $_GET['modid'] ?? '');
$type = preg_replace('/[^a-zA-Z0-9_]/', '', $_GET['type'] ?? '');
$version = preg_replace('/^\.+/', '', preg_replace('/[^0-9_.-]/', '', $_GET['version'] ?? ''));

// 构造配置文件路径
$configFile = "configs/{$modid}/{$type}/config.php";

// 加载配置文件
if (file_exists($configFile)) {
    require $configFile;

    if (!$config["$version"]) {
        die("Wrong parameter");
    } else {
        $latest_version = $version . '-' . $config["$version"]["latest"];
        $recommended_version = $version . '-' . $config["$version"]["recommended"];
        $change_logs = array_diff_key($config["$version"], array_flip(['recommended', 'latest']));
        $change_logs = array_combine(
            array_map(function ($key) use ($version) {
                return $version . '-' . $key;
            }, array_keys($change_logs)),
            array_values($change_logs)
        );

        $response = [
            "homepage" => "https://mc.vanilla.xin/{$modid}",
            "promos" => [
                "{$version}-latest" => $latest_version,
                "{$version}-recommended" => $recommended_version
            ],
            "{$version}" => $change_logs
        ];
        // 启用gzip压缩
        ob_start('ob_gzhandler');
        header('Content-Type: application/json');
        echo json_encode($response);
        // 结束输出缓冲并发送压缩后的内容
        ob_end_flush();
    }

} else {
    die("The config file does not exist");
}

