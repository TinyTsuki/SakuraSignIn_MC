# 樱花签奖励脚本示例

适用于樱花签 2.1.0，依赖香草志 2.1.0 及以上、低于 3.0.0 的版本。安装了樱花签的客户端需与服务端的网络协议 `sakura-13` 匹配；纯原版客户端仍可连接。奖励脚本只在服务端运行，客户端无需安装脚本或相应的经济插件。

## 选择并安装示例

| 示例 | 服务端依赖 | 适用环境 |
| --- | --- | --- |
| [VaultEconomyReward.java](VaultEconomyReward.java) | Vault 和已注册的经济插件 | 同时运行 Bukkit 插件及樱花签、香草志的混合服 |
| [ViScriptShopReward.java](ViScriptShopReward.java) | ViScriptShop 1.2.5 | Forge 1.20.1 / NeoForge 1.21.1 |
| [FtbMoneyReward.java](FtbMoneyReward.java) | FTB Money 1605.1.4 | Forge 1.16.5 |

1. 按上表选择脚本，在服务端安装对应的插件或模组及其依赖。它们均为可选联动，不随樱花签安装。
2. 将对应的 `.java` 文件放入服务端运行目录下的 `config/vanilla.xin/sakura_sign_in/scripts/`。
3. 重启服务端，在奖励编辑界面新增对应类型，填写金额后保存。建议先发一笔小额奖励确认到账。

脚本在服务端启动时加载，不支持热重载。改动时请编辑运行目录中的文件；仓库的 `examples` 目录不参与加载。单人游戏可退出并重新进入存档来重载适用的模组脚本。

旧目录 `config/sakura_sign_in/reward-scripts/` 仍可读取，现有奖励 ID 和配置不变。两个目录存在同名文件时，以新目录的文件为准。

> 奖励脚本拥有服务端进程的权限，没有沙箱。只安装可信脚本，并限制脚本目录的写入权限。Java 源码和字节码均不发送给客户端。

## Vault 货币

**奖励类型：Vault 货币**（英文界面显示 **Vault Currency**，ID 为 `example:vault_economy`）。

服务端需要安装 [Vault](https://github.com/MilkBowl/Vault)，以及能向 Vault 注册 `Economy` 服务的经济插件，例如 XConomy。Vault 负责转接调用，余额由经济插件管理。混合服还须能让模组或服务端线程的类加载器访问 Bukkit，并在 Bukkit 主线程执行发奖。只支持模组或只支持 Bukkit 插件的服务端均不能直接使用此示例。

金额默认 100，可填 0.01 至 1,000,000,000，最多两位小数。若经济插件只支持整数或更少的小数位，请按其精度填写。金额会显示在图标角标和领奖提示里。

脚本按 UUID 查找在线的 Bukkit 玩家，只调用一次 `depositPlayer(OfflinePlayer, double)`，以 `transactionSuccess()` 的结果确认发放。缺少经济服务、玩家已离线或金额精度不符时不会充值。如果充值结果不明，先核对余额和经济插件日志再决定是否重试。脚本不会自动重试，也不会改用控制台命令。[Vault API](https://github.com/MilkBowl/VaultAPI)

已在 Arclight 1.20.1、Vault 1.7.3 和 XConomy 2.26.3 的组合上验证通过兑换码发放。其他混合端或经济插件请先小额试领；使用 XConomy 时可用 `/bal` 对比领取前后的余额。

## ViScriptShop 金币

**奖励类型：ViScriptShop 金币**（英文界面显示 **ViScriptShop Coins**）。在 **金币数量** 字段填写金额，默认 100，可填 0.01 至 1,000,000,000，最多两位小数。这个范围是示例的校验范围，实际余额上限由 ViScriptShop 决定。图标角标会缩写金额，奖励说明保留完整数值。

脚本调用 ViScriptShop 提供的 `getMoney(ServerPlayer)` 和 `addMoney(ServerPlayer, double)`，不直接修改持久化数据。可参考 [Forge 源码](https://github.com/zhenshiz/ViScriptShop/blob/7394fec2db913e403550017d44845b4769a860ac/src/main/java/com/viscriptshop/util/ViScriptShopServerUtil.java)及 [NeoForge 源码](https://github.com/zhenshiz/ViScriptShop/blob/09f399813d1b5c6d4e7cdc8d86a690ec8a681e77/src/main/java/com/viscriptshop/util/ViScriptShopServerUtil.java)。

写入前会检查余额和浮点精度，发放后会核对余额。API 缺失或变更时不会改用命令或其他奖励。若经济模组在写入后抛出异常或触发余额上限，可能已发生部分变动。请先查余额再决定是否重试，脚本不会自动重试或回滚。示例已通过编译和模拟 API 测试，实际安装后仍需小额试领。

## FTB Money

**奖励类型：FTB Money**。在 **金币数量** 字段填写正整数，默认 100，存储格式为 `{"amount":100}`。金额必须在 Java `long` 范围内，余额会在发放前检查是否溢出。

脚本按服务端玩家参数通过反射查找 `getMoney` 和 `addMoney`，避免依赖不同环境中的 Minecraft 映射名称。其他示例也可以直接调用脚本能访问的模组 API；若插件使用独立类加载器，需要在脚本内处理查找或桥接。参见 [FTB Money API 源码](https://github.com/FTBTeam/FTB-Money/blob/main/src/main/java/dev/ftb/mods/ftbmoney/FTBMoney.java)。

API 不可用时发放失败，不会换发其他奖励。若报错但余额可能已变动，请先核对再决定是否重试。脚本奖励沿用樱花签的领奖记录机制。

## 编写自己的奖励脚本

每个文件声明一个继承 `JsonRewardScript` 的公开类，并提供可公开调用的无参构造方法。文件名与类名一致，包名固定为 `xin.vanilla.banira.generated`。编译以 Java 8 为目标；Janino 尚未实现的语法（如在同一个 `catch` 中捕获多种异常）请避免使用。

实现 `descriptor`、`grant`、`validate`、`describe` 和 `permission`。基类使用 `JsonRewardCodec` 构建 `RewardTypeDefinition<JsonObject>`，脚本无需实现泛型桥接方法；请避免用匿名类实现泛型奖励接口。

现有原生奖励和模组注册的奖励类型不可覆盖。遇到编译错误、构造失败、类型 ID 重复或默认值无效，服务端会在读取奖励配置前中止脚本加载。最多 32 个文件，单文件不超过 256 KiB，合计不超过 1 MiB。脚本准备期限为 60 秒，此期限不限制已加载脚本的任意代码执行时间。

### 可视化表单

描述符包含类型 ID、显示名称、图标物品 ID、默认 JSON 和可选的字段定义。自绘和 AUI 使用同一份表单元数据，由服务端校验内容并执行发奖。

用五参数构造方法传入 `ScriptRewardField` 列表：`integer(key, label, min, max)`、`decimal(key, label, min, max)`（上下限为字符串）、`text(key, label)` 或 `bool(key, label)`。每个字段在默认 JSON 中都要有相应的值；标签为直接显示的文本。最多 32 个字段，字段元数据合计不超过 8192 字符。

仍可用四参数构造方法。基础类型的默认值会生成表单输入；对象和数组各自保留 JSON 输入框。没有字段定义或默认字段时，使用整个对象的 JSON 编辑器。已有的未知键会在编辑时保留。

### 名称、领奖提示和角标

服务端用 `describe` 生成领奖提示，也调用 `describe(language, defaults, false)` 获取客户端语言下的类型名称。后者不应依赖默认金额。客户端语言变化或登录后才收到语言设置时，服务端只补发脚本元数据；语言未变时不重复同步。

客户端默认使用数字型 `amount` 字段生成角标，名称旁显示完整数量。要改用其他键，可调用 `descriptor.withAmountField("coins")`；传空字符串可隐藏数量。图标的物品堆叠数与奖励金额无关。角标可缩写为 `k`、`M`、`B`、`T` 等，文字说明保留完整数值。

## 指令奖励的显示名称和图标

已有的 `{"command":"..."}` 配置继续有效。可在指令奖励表单中选填 `displayName`（显示名称）和 `displayIcon`（物品 ID）。不填名称时使用默认的指令奖励名称；图标为空或不可用时使用命令方块图标。设置的名称会显示在领奖详情里。
