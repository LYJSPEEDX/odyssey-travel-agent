# Current contract

New backend output uses the [shared frontend v2 contract](v2.md), identified by both contractId and schemaVersion.
Android retains v1 support. The following is the legacy v1 field guide; use the canonical v2 schema for new integrations.

# Odyssey 行程 JSON 协议 v1

状态：Android 已接入本地 JSON 解析、详情绘制和保存；尚未连接真实模型接口。

本目录包含一份统一 JSON Schema、两份完整英文假数据、模型输出约束和验证脚本。App 按明确的 `type` 渲染固定组件，不解析攻略文字，也不让模型决定颜色、字号或任意布局。后端填写模板见 [backend/README.md](backend/README.md)，当前 App 能力与接入入口见 [android-integration.md](android-integration.md)。下方用途表描述协议支持的目标能力，不代表所有网络、地图及重排能力均已实现。

## 1. 先看哪里，以及最外层是什么

| 文件 | 用途 |
|---|---|
| `examples/canberra-3-days.json` | 最适合先阅读：3 天、16 个事件、自驾/巴士候选、3 家住宿候选 |
| `examples/new-york-10-days.json` | 10 天、47 个事件、转机、跨时区、跨日返程、汇率和入境资料缺失 |
| `itinerary.schema.json` | 每个字段的类型、必填项、可空项、枚举和类型分支；机器校验依据 |
| `model-output-instructions.md` | 交给模型的英文输出约束；与 Schema 一起使用 |
| `validate_examples.py` | Schema、ID 引用、时间与金额一致性检查，以及故意错误的反例检查 |

先阅读实际 JSON，不必先读长 Schema。Schema 是“格式规则”，example 是“模型应该返回的实际内容”。

顶层对象固定，不随目的地、天数、交通方式改变：

```text
Itinerary
├── schemaVersion / id / revision / language
├── dataMode / status / generatedAt
├── requestSnapshot
├── summary
├── days[] → events[]
├── transportGroups[] → options[] → legs[]
├── stayGroups[] → options[]
├── places[]
├── media[]
├── money
├── entryRequirements[]
├── budget
└── quality
```

| 字段 | 含义 | App 的用途 |
|---|---|---|
| `schemaVersion` | 固定 `1.0`；不是本机保存格式的 `schema: 1/2` | 选择解析器，版本不支持时拒绝读取 |
| `id` / `revision` | 稳定行程 ID、递增修改版本 | 本机保存、更改选项、避免旧响应覆盖新版本 |
| `language` | 固定 `en` | 所有模型生成的界面文案为英文；用户 notes 保留原文 |
| `dataMode` | `MOCK` / `LIVE` | 顶部是否展示 `Demo itinerary`；LIVE 不代表所有信息已验证 |
| `status` | `DRAFT` / `PARTIAL` / `READY` | 草稿、可展示但缺内容、结构与计划已完成；均不表示已预订 |
| `generatedAt` | 带时区偏移的时间 | 显示生成时间；不能替代各来源的核查时间 |
| `requestSnapshot` | 生成时采用的出发地、目的地、日期、旅客、预算和限制 | 摘要、后续修改依据、追问缺失资料 |
| `summary` | 标题、副标题、日期、天数、封面引用、亮点和总体建议 | 页面头部和旅行概览 |
| `days[]` | 按日期排列的列表，不是 day1/day2 固定字段 | Day tabs / 每日可折叠分组 |
| `transportGroups[]` | 一个起终点之间的方案组 | 交通详情及切换方案的底部面板 |
| `stayGroups[]` | 一个住宿阶段，3–5 个候选 | 住宿横向卡片；多城市有多组 |
| `places[]` | 地点实体、地址、坐标、图片引用 | 地图和详情；没有坐标就不画假位置 |
| `media[]` | 图片来源、展示地址、署名、状态 | 封面和地点图片；失败显示占位图 |
| `money` | 币种精度、换算率、换汇建议 | 币种显示和旅行须知 |
| `entryRequirements[]` | 每位旅客、每个目的地/过境国的入境资料状态 | 签证/入境提示卡和补充资料入口 |
| `budget` | 候选费用、当前方案费用引用、区间总价 | 总预算、分类费用、费用明细 |
| `quality` | 来源、假设、风险和限制匹配情况 | 风险横幅、来源链接和未满足限制的提示 |

固定必填键，空值规则也固定：无条目用 `[]`；未知的可空标量用 `null`；确认为零才用 `0`。不要返回字符串 `"null"`、空字符串替代未知，或因为某字段没数据就随机省略。

v1 给模型输出设 60 天、每天 40 个事件等防失控上限，不代表 UI 只有固定天数。超过产品上限应明确拒绝或分批生成，不能静默截断。2 个示例的金额、酒店、班次、路线和汇率仅测试 UI；不代表实际可行或可预订。

## 2. App 怎样绘制时间轴、交通和酒店

### 统一的事件入口

App 先建立 `id → object` 索引，再遍历 `days` 和其中的 `events`。顺序由数组决定，不要只按显示出来的 `09:00` 字符串排序。

| `event.type` | 所需字段 | 对应组件 |
|---|---|---|
| `TRANSPORT` | `transportGroupId` | 解析该组的 `planningOptionId`；把选中 `legs[]` 画成交通卡片或子时间轴 |
| `STAY` | `start/end`、`stayGroupId`、`action` | 从选中住宿读取酒店；`CHECK_IN` / `CHECK_OUT` 显示入住/退房卡 |
| `ACTIVITY` | `start/end`、`placeId`、`details`、`costItemIds` | 景点图片、时间、预约要求和门票 |
| `MEAL` | `start/end`、`placeId`、`details`、`costItemIds` | 用餐时间、餐厅或区域、菜系和饮食适用状态 |
| `FREE_TIME` | `start/end`、可空 `placeId`、`costItemIds` | 自由活动/休息卡；可以没有图片和地点 |

`title`、`description` 负责给人看；`type`、ID 和具体字段负责逻辑。不能从标题里是否含有 “flight” 猜测组件类型。`costItemIds: []` 表示没有附加费用条目，不自动意味着活动一定免费，可能由通票/总额覆盖；确认为免费可以增加价格为 0 且有依据的费用条目。

例如 Canberra 示例第一个事件真实内容如下：

```json
{
  "id": "event_outbound",
  "type": "TRANSPORT",
  "title": "Drive to Demo Canberra Stay 1",
  "description": "Estimated journey; not a confirmed booking.",
  "imageIds": [],
  "transportGroupId": "outbound"
}
```

读取顺序是 `event.transportGroupId` → `transportGroups[id]` → `planningOptionId` → 对应 option 的 `legs`。时间只在路段保存一份，避免事件写 09:00、航班详情却写 10:00。

### 交通方案与路段

`options[]` 是互斥的候选；某个 option 的 `legs[]` 是顺序执行的路段。UI 只显示当前选中方案进入时间轴，其余方案放在“Change transport”面板。

每个 leg 有共同字段：起终点、出发/到达时间、时长、距离及其估计依据、时间性质和信息来源。

| `leg.mode` | `details` 的固定字段 | 建议显示 |
|---|---|---|
| `FLIGHT` | marketingAirline、operatingAirline、flightNumber、机场代码、航站楼、行李、自转机标识 | 飞机图标、航空公司、机场、时区和航段；未知航班号显示 `Flight to be confirmed` |
| `DRIVE` | vehicleSource、avoidTolls、restStopPlaceIds、routeInstructions | 汽车图标、驾驶时长、休息点；油费/停车/过路费来自费用条目 |
| `BUS` / `TRAIN` / `FERRY` / `TAXI` | operator、serviceNumber、boardingPoint、alightingPoint、routeInstructions | 按 mode 选择图标，展示运营商与上下车点 |
| `WALK` | routeInstructions、accessibilityNotes | 步行图标、时长、距离和可达性提示 |

JSON Schema 用 `oneOf` 将 mode 和 details 绑定，不能给 DRIVE 填 airline 字段。多个飞行 leg 之间的空档可计算成转机等待时间；不能把等待时间算入单段飞行时长。所有 option 必须实现同一组起终点，接驳缺失要补齐路段或明确标记为未完成。

### 选择与重排

三个 ID 的含义必须区分：`recommendedOptionId` 是模型推荐，`planningOptionId` 是当前用来排时间/算钱的方案，`userSelectedOptionId` 是用户明确选择、初始为 null。`AVAILABLE` 仅表示有可展示方案，不代表班次/房间可订；酒店另有 `availability`。

切换酒店或交通是一次重新计算：用户提交行程 ID、当前 revision、方案组 ID、选项 ID；后端返回更高 revision 的完整行程。新的时间轴、接驳、预算必须一并更新，旧版可保留到新版本成功。不能只改名称、ID 或图标；往返打包票混搭尤其要重新报价。

### 日期、时区与跨日

`Moment` 使用 `{ "at": "2026-11-10T10:00:00+11:00", "timeZone": "Australia/Sydney" }`。`at` 提供明确瞬间，IANA 区域时区保留夏令时规则；二者必须一致。航班起飞与落地各显示当地时间，并显示城市或时区。

事件通常归入它开始时当地日期对应的 Day。`Day.timeZone` 是当天的主要展示时区，不强制把国际航段的出发时间改成目的地时间。跨日交通只在出发日保存一个事件，之后通过 `continuingEventIds[]` 引用，并显示 `In transit` / `Arriving today`；不重复创建交通与费用。纽约示例 Day 9 没有新事件，但会显示返程中的卡片；Day 10 显示继续中的返程及抵达后的接驳。

日期完全未定时，模型可以提出日期，并在 assumptions 明确其为建议；`requestSnapshot.dateMode` 仍为 FLEXIBLE、原始请求日期仍为 null。不要伪称用户已确认。

## 3. 费用、图片和旅行须知怎样显示

### 预算只统计当前方案，所有金额明确口径

预算作用域固定为 `ENTIRE_PARTY_WHOLE_TRIP`。金额采用**整数最小货币单位**：AUD/USD 的 18000 表示 180.00；JPY 精度为 0，18000 表示 18000。精度从 `money.currencies[].minorUnitDigits` 读取，不能对所有币种一律除以 100。相较之前讨论的十进制字符串，这份 v1 最终选择整数金额，便于 Java 精确计算；汇率仍为十进制字符串。

`MoneyRange` 包含 currency、lowMinor、expectedMinor、highMinor。一个 CostItem 有：

| 字段 | 规则 |
|---|---|
| `unitPrice` / `unit` / `quantity` | 例如每房每晚 180 AUD × 1 房 × 2 晚，quantity = 2；多人单价与全团总价不要混淆 |
| `originalTotal` | 原币种 unitPrice × quantity；low/expected/high 分别计算 |
| `exchangeRateId` | 同币种为 null；异币种使用指定方向的汇率 |
| `displayTotal` | 换算后统一展示币种的金额区间；换算率未知则为 null，不能填零 |
| `taxesAndFees` / `evidence` | 税费是否含、金额是示例/未核实/有来源；SOURCED 也不是成交价 |

`lineItems[]` 包含所有候选费用，但 `activeCostItemIds[]` 只能包含当前方案实际采用的费用，按 ID 去重。来源是选中交通、选中住宿、日程事件引用，加上 `tripCostItemIds[]` 的全程费用。候选酒店 2 和 3 的费用不会进入总价。往返票和交通通票可被多个事件引用，但只计一次。

只有整张票的 ID 才能去重；若两条费用其实是不同人、不同日期的购票，必须使用不同 ID。更换方案后不能盲目复用旧往返报价。套餐已经包含的项目不再建立第二条收费记录；说明写入 notes。

总价 = 已知有效费用小计 + contingency 预留费用。`completeness: PARTIAL` 时，UI 用 `Estimated subtotal + buffer` 并展示未包括的类别，不能宣称全包总价。所有价格已知、税费已包含且没有排除项时才可标 COMPLETE。low/high 是估计范围，不是价格保证。

汇率方向明确定义为 `1 baseCurrency = quotePerBase quoteCurrency`。换算时先按币种精度还原主单位，再转换目标币种最小单位，使用十进制运算逐条四舍五入到整数（ROUND_HALF_UP），最后汇总。不要让模型生成的总金额直接成为权威值；最终值由普通代码重算。

### 图片不影响行程主体加载

`summary.coverImageId` 指封面，`Place.imageIds` 指地点图片，`Event.imageIds` 可指定事件图片。渲染优先级：事件图 → 地点图 → 对应类别的本地占位图。示例的图片状态故意为 PENDING，URL 为 null，验证没有图片也能有完整数据；本轮没有下载或授权任何图片。

`media` 记录 provider、可用地址、作者署名和过期信息。READY 才能尝试加载；过期、加载失败、PENDING、UNAVAILABLE 一律退回占位图。OWNED 素材可以使用 App 已注册的 assetKey，第三方图像必须遵守各自来源的署名、缓存、热链接等规则。assetKey 由 App 白名单解析，不是任意本地文件路径。展示 URL 应为经服务端审核的 HTTPS 来源，不能将模型生成的任意网址交给客户端或后端抓取。

长期保存应用自己的 media.id 和可合法保留的地点标识；不要把第三方短期 photo name / URL 当作永久有效标识。需要时重新解析媒体；不允许把 Google Places 照片批量永久复制进自有素材库。图片解析结果、署名和来源由服务填充，不能让模型凭空生成。

### 签证、换汇和限制检查

entryRequirements 按旅客 ID 与国家展示；护照信息不全显示 `More information needed`，不能从悉尼出发推断持澳洲护照。requiredInputFields 是追问项目，不表示 requestSnapshot 已提供所有字段。v1 的 requestSnapshot 只保留最低限度输入，额外证明材料属于后续独立输入协议，不存护照号码或扫描件。

入境来源必须经独立查询核对，模型不能自行把状态提升为 REVIEWED。money.exchangeAdvice 存换汇建议及可选具体 placeId；无可靠商家来源时，只显示一般比较建议。约束有 HARD / SOFT，quality.constraintChecks 对每项返回 SATISFIED / UNVERIFIED / CONFLICT。出现无法满足的 HARD 约束，返回 PARTIAL 和 BLOCKING 提示，不允许静默忽略。

## 4. 模型、普通后端代码、App 各负责什么

| 负责方 | 职责 |
|---|---|
| 模型 | 根据输入提出标题、日程、交通/住宿候选、估计范围、假设和待核实内容；输出 JSON 而不是 Markdown 攻略 |
| 后端查询/媒体层 | 解析真实地点、航班、照片和来源；校验外部链接与授权；未接接口时保持未知或 MOCK |
| 后端校验/计算层 | JSON Schema 校验、时间/引用/硬约束检查、金额计算、revision 管理；不能完全信任模型返回的 totals、sources 和 verified 状态 |
| Android | 按 type 选择固定组件、解析引用、显示风险/占位、提交用户选择、保存通过校验的行程 |

模型输出草稿和最终 App JSON 可以共享字段结构，但**模型原始字符串不能直接等同于可信响应**。原始输出必须先验证。Schema 不支持的模型服务，需要一个对应的 structured-output 适配器；不是把这个文件直接塞给任何模型都一定能运行。尤其要验证 provider 对 oneOf、format、范围和引用的支持。

生成过程不把半截 JSON 渲染成最终结果。App 显示加载状态，成功后解析完整对象；失败返回独立错误对象而不是塞进 days。建议错误形态为 `{ "error": { "code": "INVALID_MODEL_OUTPUT", "message": "Please try again.", "retryable": true } }`；它不是本 Schema 的成功响应。

当前 `SavedTrip` 的 itinerary 是文本，后续接入需要新的解析/渲染层与持久化版本。旧记录仍应使用旧详情页或明确迁移，不能直接把旧文本当新 JSON 解析。本轮不做该迁移。

## 5. 已验证什么，仍然没验证什么

运行（需 Python 3.9+、jsonschema 4.x，建议独立环境）：

```sh
python3 docs/itinerary-contract/validate_examples.py
```

脚本校验两份示例的 Schema、唯一 ID、主要外键、币种/时区、路段连接与时长、事件顺序、跨日引用、住宿晚数、3 个住宿候选、选中方案费用去重、数量/汇率换算与总价，并用 8 个损坏样本证明能拒绝错误。

脚本**不是生产级行程可行性验证器**：尚未实现实际道路/航线核查、转机最短时间、营业时间、所有地点间的地理连续性、签证规则、硬约束真实性、图片版权审查或模型服务适配。它也没有测试 Android 新渲染器，因为这次没有实现新页面。

本例预算都是演示：Canberra 全团示例期望金额含预留为 AUD 1,030；New York 为 AUD 9,085；二者均为 PARTIAL、未包括所有费用，不是报价。纽约的 10 天包含飞行与回到悉尼当天，并不是在纽约停留 10 个完整白天。

接口方案已经固定了可变列表和类型分支，下一步评审应关注卡片内容是否足够，再决定是否实现 Android 模型与渲染器；不需要为每种旅行重新设计一套返回结构。

参考规范：[JSON Schema 类型分支](https://json-schema.org/understanding-json-schema/reference/combining)、[可变长度列表](https://json-schema.org/understanding-json-schema/reference/array)。图片接入另需遵守 [Google Places Photos](https://developers.google.com/maps/documentation/places/web-service/place-photos) 和各图片提供方的现行规则。
