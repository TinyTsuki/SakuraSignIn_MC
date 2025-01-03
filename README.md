# Sakura Sign-In (樱花签)

一个 Minecraft Forge 签到奖励 Mod。

## 目录

- [Sakura Sign-In](#sakura_sign_in)
    - [目录](#目录)
    - [介绍](#介绍)
    - [特性](#特性)
    - [配置说明](#配置说明)
    - [注意事项](#注意事项)
    - [许可证](#许可证)

## 介绍

本项目适用于Minecraft Forge服务器，实现每日签到/登入奖励，促进玩家活跃度。
该Mod服务器必装，客户端可选。

## 特性

- **每日签到奖励**：可以设置每日签到基础奖励。
- **累计签到奖励**：可以设置累计签到天数奖励。
- **周期签到奖励**：可以设置周期性（连续签到第5、12天奖励循环）签到奖励。
- **连续签到奖励**：可以设置连续签到奖励，每天/周/指定周期奖励数量(等)递增。
- **节日/纪念日签到奖励**：可以设置具体的某一天~某段时间的奖励。
- **可变的日期重置时间**：签到间隔可动态设置；亦或者当天04:00~第二天03:59点算作一天， 而不是当天00:00~第二天23:59算做一天。
- **签到页面主题可自定义**：签到页面主题可完全自定义。
- **奖励配置可视化编辑**：可视化添加修改奖励配置，支持物品nbt标签，可视化选择奖励物品。
- **很烂的代码**：烂代码 + 疏忽的测试 = 一堆难闻的臭虫。

## TODO

- **添加新的奖励规则**：添加兑换码奖励规则 与 随机奖励规则。
- **奖励可设置领取条件**：奖励可设置领取条件，如需满足等级、经验、物品、连续签到天数、其他mod的自定义等级、其他mod的虚拟货币等条件。
- **奖励可设置自定义倍率**：奖励可设置领取倍率，倍率可为 等级、经验、物品、连续签到天数、其他mod的自定义等级、其他mod的虚拟货币等数值。
- **主题配置可视化编辑**：可视化修改主题样式等。
- **在线时长统计**：统计在线时长，并添加相应奖励规则。
- **MORE**：...

## 配置说明

1. **Mod基本设置**：

    - 客户端配置文件 `config/sakura_sign_in-client.toml`。
      ```toml
      [client]
      	#签到或补签时是否自动领取奖励。
      	autoRewarded = false
      	#签到页面是否显示上个月的奖励。
      	showLastReward = false
      	#签到页面是否显示下个月的奖励。
      	showNextReward = false
      	#主题材质路径，可为外部路径： config/sakura_sign_in/themes/your_theme.png
      	theme = "textures/gui/sign_in_calendar_original.png"
      	#是否使用内置主题特殊图标。
      	specialTheme = true
      ```

    - 服务端配置文件 `world/serverconfig/sakura_sign_in-server.toml`。
      ```toml
      [server]
      	#签到时间冷却时间，以小数表示时间，整数部分为小时，小数部分为分钟。
      	#若timeCoolingMethod=FIXED_INTERVAL，则表示玩家在上次签到12小时34分钟(默认)后刷新签到冷却；
      	#若timeCoolingMethod=MIXED，则表示每天4.00(默认)刷新签到冷却，并且需要距离上次签到12小时34分钟(默认)后才能再次签到。
      	#Range: 0.0 ~ 23.59
      	timeCoolingInterval = 12.34
      	#是否允许玩家在进入服务器时自动签到。
      	autoSignIn = false
      	#签到时间冷却时间，以小数表示时间，整数部分为小时，小数部分为分钟。
      	#若timeCoolingMethod=FIXED_TIME(默认)，则表示每天4.00(默认)刷新签到冷却；
      	#若timeCoolingMethod=MIXED，则表示每天4.00(默认)刷新签到冷却，并且需要距离上次签到12小时34分钟(默认)后才能再次签到。
      	#Range: 0.0 ~ 23.59
      	timeCoolingTime = 4.0
      	#签到时间冷却方式。 FIXED_TIME: 固定时间， FIXED_INTERVAL: 固定时间间隔， MIXED: 混合模式。
      	#Allowed Values: FIXED_TIME, FIXED_INTERVAL, MIXED
      	timeCoolingMethod = "FIXED_TIME"
      	#补签卡最远可补签多少天以前的漏签。
      	#Range: 1 ~ 365
      	reSignInDays = 30
      	#使用补签卡进行补签时是否仅获得基础奖励。
      	signInCardOnlyBaseReward = false
      	#是否允许玩家使用补签卡进行补签。(不是签到卡哦)
      	#可以在签到奖励里面添加物品[sakura_sign_in:sign_in_card]来获得补签卡。
      	signInCard = true
      	#实际时间，与 服务器原时间 配合计算服务器时间偏移以校准服务器时间。
      	serverCalibrationTime = "2024-01-01 00:00:00"
      	#服务器原时间，与 实际时间 配合计算服务器时间偏移以校准服务器时间。
      	serverTime = "2024-01-01 00:00:00"
      ```

2. **签到奖励配置**：

    - 打开配置文件 `config/sakura_sign_in/sign_in_data.json`。
      ```json
      {
        "baseRewards": [
          {
            "type": "ITEM",
            "content": {
              "item": "minecraft:apple",
              "count": 1
            }
          }
        ],
        "continuousRewards": {
          "1": [
            {
              "type": "EXP_POINT",
              "content": {
                "expPoint": 5
              }
            }
          ],
          "4": [
            {
              "type": "ITEM",
              "content": {
                "item": "minecraft:cake",
                "count": 1
              }
            }
          ],
          "8": [
            {
              "type": "SIGN_IN_CARD",
              "content": {
                "signInCard": 1
              }
            }
          ]
        },
        "cycleRewards": {
          "2": [
            {
              "type": "EXP_POINT",
              "content": {
                "expPoint": 3
              }
            }
          ],
          "5": [
            {
              "type": "EXP_LEVEL",
              "content": {
                "expPoint": 1
              }
            }
          ]
        },
        "yearRewards": {
        },
        "monthRewards": {
        },
        "weekRewards": {
          "6": [
            {
              "type": "EFFECT",
              "content": {
                "effect": "minecraft:luck",
                "duration": 6000,
                "amplifier": 1
              }
            }
          ],
          "7": [
            {
              "type": "EFFECT",
              "content": {
                "effect": "minecraft:heal",
                "duration": 6000,
                "amplifier": 0
              }
            },
            {
              "type": "EFFECT",
              "content": {
                "effect": "minecraft:jump",
                "duration": 6000,
                "amplifier": 0
              }
            },
            {
              "type": "ITEM",
              "content": {
                "item": "minecraft:enchanted_golden_apple",
                "count": 1
              }
            }
          ]
        },
        "dateTimeRewards": {
          "0000-10-06~1": [
            {
              "type": "ITEM",
              "content": {
                "item": "minecraft:experience_bottle",
                "count": 1
              }
            },
            {
              "type": "EFFECT",
              "content": {
                "effect": "minecraft:damage_resistance",
                "duration": 1,
                "amplifier": 300
              }
            }
          ]
        }
      }
      ```
      以上是一个示例配置，你可以根据你的需求进行修改。
    - 参数解读：
        - `baseRewards`：基础签到奖励，每次签到都会获得。
        - `continuousRewards`
          ：连续签到奖励，连续签到一定天数后，会获得相应的奖励，签到天数不足下一个奖励标准但是满足上一个标准时将一直会获得上一个标准的奖励。例如：按照示例配置，连续签到到第1、2、3天，会获得5点经验值；连续签到到第4、5、6、7天，会获得1块蛋糕；连续签到8天及以后会获得1个补签卡。
        - `cycleRewards`
          ：连续签到循环奖励，连续签到一定天数后，会获得相应的奖励，连续签到天数大于最大天数时重新从第一个奖励开始计算。例如：按照示例配置，连续签到到第2、3、4、7、8、9天，会获得3点经验值；连续签到到第5、10天，会获得1级经验等级。
        - `yearRewards`：按年签到奖励，签到该年的第x天，会获得相应的奖励。
        - `monthRewards`：按月签到奖励，签到该月的第x天，会获得相应的奖励。
        - `weekRewards`
          ：按周签到奖励，签到该周的第x天（周x），会获得相应的奖励。例如，按照示例配置，在周六签到会获得持续100分钟的幸运Ⅰ效果；在周日签到会获得持续100分钟的生命回复Ⅰ效果、持续100分钟的跳跃提升Ⅰ效果及1个附魔金苹果。
        - `dateTimeRewards`：指定日期/日期范围签到奖励，可以指定日期或日期范围，签到该日期/日期范围，会获得相应的奖励。
          例如：按照示例配置，在每年的10月06日至10月07日签到，会获得1个经验瓶子及持续5分钟的抗性Ⅱ效果。指定日期/
          日期范围签到奖励，日期格式支持 yyyy-MM-dd, 0000-MM-dd, yyyy-MM-00, 0000-MM-00, yyyy\~n-MM\~n-dd,
          0000-MM\~n-dd\~n,
          yyyy-MM\~n-00, 0000-MM\~n-00, yyyy-MM-ddTHH:mm, yyyy-MM-ddTHH\~n:mm\~n。
          指定具体时间时, 日期与时间需要'T'分隔；\~n表示区间, 例 2024-10-05\~5 表示 2024年10月05日到10日的5天；0000(yyyy)
          表示不限年份, 00(MM) 表示不限月份, 00(dd) 表示不限日期。

## 注意事项

- **签到时间问题**：请在进入服务器后使用『/va config get date』指令确认服务器时间是否正确，若与实际时间不符，请使用指令『/va
  config set date \<yyyy> \<MM> \<dd> \<HH> \<mm> \<ss>』将服务器时间校准，否则可能会出现签到时间异常或无法签到等问题。

## 许可证

MIT License

---

如有任何问题或建议，欢迎提交 Issues 或 Pull requests。
