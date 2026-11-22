# Server-local reward scripts

Available in Sakura 2.1.0 with Banira 2.1.0 or newer (below 3.0.0).
Requires matching Sakura builds on modded clients
(network protocol `sakura-13`).
Vanilla clients remain optional. Never send Java source or bytecode to clients.

Place trusted Java source files in `config/sakura_sign_in/reward-scripts/` and
restart the server. Each file must declare a public no-argument implementation
of `JsonRewardScript`, with the filename matching its class name and the package
`xin.vanilla.banira.generated`. Java 8 syntax is supported. This is full-trust
server code, not a sandbox; only administrators should write this directory.

Implement `descriptor`, `grant`, `validate`, `describe` and `permission`. The
`JsonRewardScript` base class builds a `RewardTypeDefinition<JsonObject>` with
`JsonRewardCodec`, keeping generic bridge methods out of Janino source. Avoid
implementing generic reward interfaces with anonymous classes in scripts.
Existing built-in/mod reward types cannot
be replaced. Compilation, constructor, duplicate-ID or invalid-default failures
abort loading before reward configuration is read. There is no live reload.
Limits: 32 files, 256 KiB per file, 1 MiB combined source, 60-second preparation
deadline. These limits do not interrupt arbitrary code inside trusted scripts.

The descriptor contains the type ID, display name, item registry ID and default
JSON data and optional field definitions. Both native and AUI modes render the
same form. Actual validation and reward execution remain server-side.
Use the five-argument descriptor constructor with a list of `ScriptRewardField`
definitions: `integer(key, label, min, max)`, `decimal(key, label, min, max)`
(decimal bounds are strings), `text(key, label)` or `bool(key, label)`.
Every declared field needs a matching default value. Labels are literal text.
Up to 32 fields and 8192 characters of field metadata are supported.
Old four-argument descriptors still work: primitive defaults produce form fields
automatically; object/array values keep individual JSON inputs. Types with no
declared/default fields retain the whole-object JSON editor. Existing unknown
keys survive editing. No scripts execute on the client.
The script's describer supplies the server-rendered claim message, including any
amount. Clients without the script can display the type name and item icon.

The server also uses `describe(language, defaults, false)` to localize the type
name sent to that player. Keep this result independent of the default amount.
This UI name follows the client's Minecraft language. When client settings arrive
after the initial connection, or the language changes, the server resends only
the script metadata; unchanged languages do not cause repeated synchronization.
Numeric `amount` fields automatically supply the icon badge and full amount in
the client label. Use `descriptor.withAmountField("coins")` for a different key,
or an empty string for no quantity. Icon stack size is unrelated to currency.
Badges use compact units (`k`, `M`, `B`, `T`, etc.); labels keep the exact value.
Scripts load when the server starts; edit the runtime `config` copy, then restart
the server (or leave and re-enter a single-player world). `examples` is not watched.

## FTB Money example

`FtbMoneyReward.java` targets the FTB Money **Forge 1.16.5 / 1605.1.4** API:
<https://github.com/FTBTeam/FTB-Money/blob/main/src/main/java/dev/ftb/mods/ftbmoney/FTBMoney.java>

Install FTB Money and its dependencies separately. The example is not installed
automatically and no FTB dependency is bundled in Sakura. Put this file in the
script directory and restart; select **FTB Money** when adding a reward. The
form contains a **金币数量** (coin amount) field, initially 100. Its stored
data remains `{"amount":100}`. Only positive whole numbers fitting a Java
`long` are accepted; a balance overflow is rejected before adding funds.

The example resolves FTB's public server-player `getMoney` and `addMoney`
overloads reflectively to avoid Minecraft mapping-name differences in production
JARs. Other scripts may use directly visible mod APIs. Plugin environments with
separate class loaders must supply their own lookup/bridge inside the script.

If the target API is unavailable, the reward fails without substituting another
reward. As with any external side effect, an exception after the target has
already changed its data must be investigated by an administrator; do not
blindly retry it. Script rewards follow Sakura's existing claim receipt policy.

## Command reward presentation

Old `{"command":"..."}` content still works. Optional `displayName` and
`displayIcon` (an item registry ID) can be set in the command reward form.
An empty name retains the standard command-reward label; an empty or unavailable
icon uses the command-block icon. A configured name is used in claim details.
