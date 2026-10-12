# 后端填写模板（v1）

`../itinerary.schema.json` 是唯一权威协议；本目录不改变协议。`../examples/canberra-3-days.json`、`../examples/new-york-10-days.json` 是完整、可测试的 MOCK 响应。App 暂不接网络；本文不代表任何具体前端功能已经实现。

## 1. 使用步骤

1. 将原 Schema、`../model-output-instructions.md`、用户请求和本目录模板一起提供给模型。
2. 根据真实请求填写 `response.template.json`，从 `variants.template.json` 复制所需分支。
3. 后端补充可信查询结果并重新计算时间、费用与版本。
4. 对完整结果执行 Schema 和业务校验，通过后才作为成功响应。

两个模板只提供填写形状，**不是成功响应，也不是有效行程**。模板中的 `null` 有两种含义：Schema 允许为空的字段可保留 `null`；Schema 不允许为空的标量使用 `null` 表示“尚未填写”，必须替换为正确类型和值。模板所有普通字符串、整数、布尔值和枚举均留空，只有 Schema 的 `const` 与用于区分分支的 `type` / `mode` 已填写。不得将模板直接传给 App，或把空字符串、`0`、`false` 当作未知值。

对象数组里的一个对象只是形状示意；不是已经存在的旅客、酒店或行程日。删除无需的示意对象，按实际数据复制和填写；必需集合必须达到 Schema 的最小数量。字符串数组 `[]` 表示待填写的集合，不表示业务上已确认没有条目。`variants.template.json` 的 `events`、`transportLegs`、`structures` 是模板目录键，不能加入成功响应顶层。其 `structures` 提供所有公共定义，包括 Money、MoneyRange、Moment、Evidence、住宿、来源和费用。

## 2. 字段、类型与枚举

顶层固定包含 `schemaVersion/id/revision/language/dataMode/status/generatedAt/requestSnapshot/summary/places/media/transportGroups/stayGroups/days/money/entryRequirements/budget/quality`，不得新增键。`schemaVersion = "1.0"`、`language = "en"`；`revision` 是至少为 1 的整数。所有生成的界面文案为英文，用户 notes 保留原文。

| 结构 | 字段与类型规则 |
|---|---|
| requestSnapshot | 出发地 ID 字符串、目的地 ID 数组、日期模式和返程含义枚举；请求日期可空；travelers 为对象数组（1–20 人）；预算 target 可空，includeIntercityTransport 为布尔值；constraints/notes 为数组 |
| summary / days | 标题与摘要为非空字符串；日期为 `YYYY-MM-DD`；dayCount/dayNumber 为正整数；封面 ID、walkingDistanceMeters 可空；events/continuingEventIds 为数组 |
| places / media | ID、名称、类别与来源明确；地址、坐标、providerPlaceId 及媒体 URL 等可空字段按 Schema 判断；不可编造地点坐标、图片地址和署名 |
| transportGroups / stayGroups | options 为对象数组；推荐、计划和用户选择 ID 按 Schema 可空；AVAILABLE 组必须有实际可用的计划引用，住宿有 3–5 个不同候选；未知酒店可返回 PENDING/UNAVAILABLE |
| money / budget | 币种精度是整数；金额为整数最小货币单位；汇率为十进制字符串；未知可空价格为 null，不能用零代替 |
| entryRequirements / quality | travelerIds/sourceIds/relatedIds 等为 ID 数组；来源、假设、提示、约束检查为各自固定结构；缺护照信息时 NEEDS_INPUT，不存护照号或扫描件 |

类型细节、必填和可空字段以 Schema 的 `required`、`type`、`anyOf` 为准。无条目的集合用 `[]`；只有明确允许 `type: null` 的最终字段才能用 `null`；对象不能用字符串替代，金额不能用字符串替代。模板故意未填的 `null` 是唯一的类型例外。

| 字段 | 枚举 |
|---|---|
| dataMode / status | MOCK, LIVE / DRAFT, PARTIAL, READY |
| dateMode / returnDateMeaning | FIXED, FLEXIBLE / ARRIVE_HOME, LEAVE_DESTINATION |
| companions | SOLO, COUPLE, FRIENDS, FAMILY, CUSTOM |
| event.type | TRANSPORT, STAY, ACTIVITY, MEAL, FREE_TIME |
| leg.mode | FLIGHT, DRIVE, BUS, TRAIN, FERRY, TAXI, WALK |
| timingStatus / distanceBasis | ESTIMATED, SCHEDULED / ROUTE_ESTIMATE, GREAT_CIRCLE, UNKNOWN |
| group.status / availability | AVAILABLE, PENDING, UNAVAILABLE / UNKNOWN, AVAILABLE, UNAVAILABLE |
| evidence.status / budget.completeness | MOCK, UNVERIFIED, SOURCED / COMPLETE, PARTIAL |
| constraint.strength / check.status | HARD, SOFT / SATISFIED, UNVERIFIED, CONFLICT |
| entry.role / entry.status | DESTINATION, TRANSIT / NEEDS_INPUT, NEEDS_VERIFICATION, REVIEWED, NOT_APPLICABLE |
| notice.severity / stay.action | INFO, WARNING, BLOCKING / CHECK_IN, CHECK_OUT |

地点类别、媒体 provider/usage/status、约束 code、活动 category、预约、餐饮、车辆、费用 category/unit/taxesAndFees 的完整枚举在 Schema 对应定义中；不得自造值。`FLIGHT.details` 为 Flight，`DRIVE.details` 为 Drive，BUS/TRAIN/FERRY/TAXI 共用 Transit，WALK 使用 Walk；不可混用字段。TRANSPORT 事件只关联 transportGroupId，没有事件自身的时间副本。

## 3. 关联、时间和费用

所有 ID 稳定且唯一，引用必须存在并指向正确实体。`placeId/fromPlaceId/toPlaceId/originPlaceId/destinationPlaceIds` 引用 places；图片 ID 引用 media；transportGroupId/stayGroupId 引用方案组；costItemIds 引用 budget.lineItems；travelerIds 引用 requestSnapshot.travelers；sourceIds 引用 quality.sources；exchangeRateId 引用 money.exchangeRates；constraintId 引用 requestSnapshot.constraints。`continuingEventIds` 引用先前日开始且仍在继续的事件。

`recommendedOptionId` 是推荐，`planningOptionId` 是排时间和预算的选项，`userSelectedOptionId` 仅真实用户选择后填写。选项 ID 必须属于该组。options 是互斥候选，legs 是同一方案顺序执行的路段；不能把所有候选叠加到时间轴或预算。切换选项要返回递增 revision 的完整重算结果。

Moment 是 `{ "at": "2026-11-10T10:00:00+11:00", "timeZone": "Australia/Sydney" }`：显式偏移必须符合 IANA 时区。按真实瞬间比较时间，航段分别使用出发/抵达当地时间；durationMinutes 不含转机等待。跨日交通只创建一次，通过后续日期的 continuingEventIds 关联。Day.timeZone 是主要展示时区。灵活日期的建议不能冒充用户已确认日期。

预算 scope 固定 ENTIRE_PARTY_WHOLE_TRIP。AUD/USD `18000` 通常表示 180.00，JPY 精度 0 时表示 18000；实际精度取 money.currencies.minorUnitDigits。MoneyRange 为 currency/lowMinor/expectedMinor/highMinor；单价乘 quantity 得原币总价，low ≤ expected ≤ high。汇率方向为 `1 baseCurrency = quotePerBase quoteCurrency`，后端使用十进制运算、逐条 ROUND_HALF_UP 到目标最小单位后汇总。

activeCostItemIds 只纳入规划选中的交通和住宿、事件费用及 tripCostItemIds，并按 ID 去重；往返打包票不能重复计费。subtotal 加 contingency 得 total。未知价格/税费/排除项必须保留 PARTIAL，不能宣称全包报价；READY、AVAILABLE、SOURCED 均不等于已预订或可成交。

## 4. 模型与后端职责

模型提出日程、候选、估计、假设与待核实内容。真实地点、航班、图片、URL 审查与署名由后端查询层填充；来源 URL 和 checkedAt 只能取可信检索结果；入境 REVIEWED 必须由后端核查。HARD 约束无法满足时返回 PARTIAL 与 BLOCKING 提示。MOCK 数据明确标演示；LIVE 缺事实时保留 UNVERIFIED，不能编造确认信息。

后端独立校验 JSON Schema、ID 引用、路段连接、时间、住宿晚数、约束、金额和 revision；不要信任模型总价。Schema 兼容性取决于模型提供方，可能需要 structured-output 适配器。错误响应独立于成功 Schema；半截 JSON 不作为成功结果。

可直接复制以下英文提示词，并附上 Schema、原模型约束及用户请求：

```text
Return exactly one complete Odyssey itinerary JSON object conforming to the attached itinerary.schema.json (v1.0). Use response.template.json only as a shape guide and variants.template.json only as a catalog of event, transport-leg and shared object shapes. Never return the catalog wrapper. Template nulls in non-nullable fields are unfilled placeholders: replace every such placeholder with the correct type and a truthful value before returning. Do not return Markdown, comments, additional keys, empty strings as unknown values, or fabricated confirmations.

Use English for generated UI text and preserve original user notes. Include all required fields. Use null only where the schema permits it and empty arrays only for genuinely absent collections. Produce stable unique IDs and resolve every reference. Respect exact event type and leg mode variants. Options are alternatives; legs are sequential. Only planning options contribute to the timeline and active costs. User selection remains null unless explicitly supplied. AVAILABLE stay groups require 3–5 distinct candidates; use PENDING/UNAVAILABLE and PARTIAL if trusted live data is insufficient.

Use explicit UTC offsets matching IANA time zones. Store cross-day transport once and reference it on later days. Use integer minor currency units and declared currency precision; exchange rates are decimal strings. Deduplicate selected costs by ID, include known mandatory fees, and keep incomplete budgets PARTIAL. The backend will recompute totals and verify all references and time relationships.

Never invent retrieved sources, coordinates, image URLs, attribution, availability or reviewed entry advice. Copy trusted retrieval evidence only. MOCK fixtures must clearly identify demonstration data; unsupported live assertions remain UNVERIFIED. Missing traveler information requires NEEDS_INPUT. An unmet HARD constraint requires PARTIAL with a BLOCKING notice. READY does not mean booked. Follow the attached model-output-instructions.md and provide truthful DRAFT/PARTIAL content when the plan cannot be completed.
```

## 5. 验证

运行 `python3 docs/itinerary-contract/backend/validate_templates.py` 验证模板 JSON 语法、全部对象键、必填字段、类型、分支与完整覆盖。脚本仅允许模板标量 `null` 的未填写例外，不能代替真实响应的 Schema 校验。

完整示例使用 `python3 docs/itinerary-contract/validate_examples.py` 验证；该脚本需要环境已有 jsonschema，不新增依赖。它不证明实际航线可行、酒店有房、签证正确或图片授权。
