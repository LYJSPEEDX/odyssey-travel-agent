# 用户档案与逐轮规划 JSON 协议设计

日期：2026-10-23（Australia/Sydney）

状态：用户已确认方案，并要求扩充至 60 个标签、Companions 多选；待后端负责人联调评审，不是已上线接口。本轮使用 Mock，不连接真实服务。

## 1. 范围与方案选择

在现有 Java/XML/Material UI 上扩展首次资料采集，保留旅行杂志＋登机牌样式、英文 UI、既有 itinerary 渲染与本机行程。规划改为出发地预选＋固定七轮；每次业务提交经过请求 JSON、Mock、响应 JSON、校验，再更新 UI，不允许 Mock 直接修改 View 或表单状态。

比较两个方案：

| 方案 | 优点 | 代价 | 决定 |
|---|---|---|---|
| 统一规划会话：按动作提交，返回完整状态 | 共享版本号、错误语义，回退与动态问题容易保持一致 | 需要明确会话状态机 | 推荐 |
| 每一步独立接口、前端拼接状态 | 单个接口直观 | 上游修改、并发、恢复逻辑易重复且不一致 | 不采用 |

本文件定义拟采用的字段、类型与状态规则。一组完整独立请求/响应示例在 [planning-session](../../planning-session/README.md)。下一实施阶段将其固化为 JSON Schema、全部轮次示例和验证测试；这些完整实现尚未在本轮完成。接口格式须经后端负责人认可后才算联调约定，不能把 App 一方的设计称为双方已确认。

## 2. 本地用户档案 v2

这是无登录的本地档案，不是账号或身份凭证。保留现有 `profile.json` 文件及 AtomicFile 写入方式。v1 数据先读取并保留，进入补充资料页预填年龄、常住地、旧偏好备注；用户完成并成功保存后才写入 v2，不虚构名字、性别或国籍。损坏/未知版本不得静默覆盖。

本地完整示例（虚构资料）：

```json
{
  "schema_version": 2,
  "display_name": "Alex",
  "gender": "PREFER_NOT_TO_SAY",
  "age": 25,
  "nationality_country_code": "AU",
  "residential_location": "Sydney, Australia",
  "travel_tag_ids": ["ISLANDS", "FOOD", "SLOW_TRAVEL"],
  "travel_preferences": "Prefer quiet neighbourhoods"
}
```

所有键必填；可空字段见下表。拒绝未知字段，不把数值字符串自动转整数。

| 字段 | 类型/约束 | 说明 |
|---|---|---|
| schema_version | integer，固定 2 | 本地存储版本，不是 itinerary 版本 |
| display_name | 非空 string，1–80 Unicode 字符 | 昵称即可，不要求法定姓名 |
| gender | FEMALE / MALE / NON_BINARY / OTHER / PREFER_NOT_TO_SAY | 可选不透露；不得据此推断偏好 |
| age | integer，1–120 | 延续现有范围；年龄不是生日 |
| nationality_country_code | ISO 两位国家码或 null | null 表示未提供；不是常住国，也不是护照验证 |
| residential_location | 非空 string，最多 200 字符 | 手动填写，不请求定位权限，不自动当作已确认出发地 |
| travel_tag_ids | 去重 string[]，0–60 项 | 稳定 ID，不发送标签显示文案作为 ID |
| travel_preferences | string，最多 2000 字符 | 自由补充，可为空；保留 v1 内容 |

界面将资料与偏好分为两个小页面。标签自动换行，描边胶囊、多选、再次点击取消；选中同时显示底色和勾选，不只依赖颜色。可不选标签；设置提供修改入口。资料保存成功再进入首页。

固定目录 v1（60 个，后续改显示名称不得改变 ID；尚未发布，所以仍为 v1）：

| ID | English label | ID | English label | ID | English label |
|---|---|---|---|---|---|
| INTERNATIONAL | International | DOMESTIC | Domestic | WEEKEND_BREAKS | Weekend breaks |
| LONG_HAUL | Long-haul trips | ROAD_TRIPS | Road trips | RAIL_TRAVEL | Train journeys |
| ISLANDS | Islands | BEACHES | Beaches | MOUNTAINS | Mountains |
| COUNTRYSIDE | Countryside | CITY_BREAKS | City breaks | NATURE | Nature |
| SHOPPING | Shopping | FOOD | Local food | CAFES | Cafés |
| MUSEUMS | Museums | ART | Art & galleries | HISTORY | History |
| ARCHITECTURE | Architecture | CULTURE | Local culture | PHOTOGRAPHY | Photography |
| MUSIC | Live music | FESTIVALS | Festivals | NIGHTLIFE | Nightlife |
| HIKING | Hiking | CYCLING | Cycling | WATER_ACTIVITIES | Water activities |
| WILDLIFE | Wildlife | ADVENTURE | Adventure | WELLNESS | Wellness |
| SLOW_TRAVEL | Slow travel | PACKED_ITINERARY | See more places | HIDDEN_GEMS | Hidden gems |
| LUXURY | Luxury stays | BUDGET_FRIENDLY | Budget-friendly | ECO_CONSCIOUS | Eco-conscious |
| DIVING | Diving | SNORKELING | Snorkeling | FREEDIVING | Freediving |
| SURFING | Surfing | KITESURFING | Kitesurfing | WINDSURFING | Windsurfing |
| KAYAKING | Kayaking | CANOEING | Canoeing | PADDLEBOARDING | Paddleboarding |
| SAILING | Sailing | RAFTING | Rafting | SWIMMING | Swimming |
| SKIING | Skiing | SNOWBOARDING | Snowboarding | ICE_SKATING | Ice skating |
| ROCK_CLIMBING | Rock climbing | BOULDERING | Bouldering | TRAIL_RUNNING | Trail running |
| RUNNING | Running | MOUNTAIN_BIKING | Mountain biking | HORSE_RIDING | Horse riding |
| TENNIS | Tennis | GOLF | Golf | SPORTS_EVENTS | Sports events |

标签表示初始偏好，不是硬限制；矛盾偏好交由问卷澄清，不能自行删除用户选择。同行人、无障碍、素食等仍在本次旅行问卷表达，不由性别/国籍推断。

运动标签只表达兴趣，不代表拥有潜水证、技能、装备或适合参加某活动；后续动态问题可询问经验等级，不能因勾选 Diving 就安排需要资质的具体项目。界面按水上、山地/冬季、其他运动和旅行主题分组展示，不把 60 个标签挤进固定高度容器。

### 对后端披露的最小档案

创建规划会话时从本地档案显式映射为 `profileContext`：仅发送 age、nationalityCountryCode、residentialLocation、travelTagIds、travelPreferences、tagCatalogVersion。display_name、gender 默认仅本地展示，不传模型；未来真实联网前明确告知将发送哪些字段。档案上下文在创建会话时固定，设置里修改档案只影响新会话，避免半程替换用户偏好。

无 accountId、密码、邮箱、护照号码；sessionId 不等于登录授权。未来远程服务的匿名会话访问凭证须与业务 JSON 分离，不能仅凭可猜的 ID 读取其他会话。

## 3. 固定轮次与动作

| stepId | UI 序号 | 输入形式 | 下一步规则 |
|---|---|---|---|
| ORIGIN | Departure，预选页（用户所说 0.5 轮） | 推荐城市或文本 | 文本 REFINE 留在本轮；选中后 CONFIRM |
| DESTINATION | 01 / 07 | 城市卡（城市名＋三个关键词）或文本 | 文本 REFINE 留在本轮；选中后 CONFIRM |
| DATES | 02 / 07 | 日历区间、建议区间或灵活日期说明 | 文本 REFINE 留在本轮；选中后 CONFIRM |
| COMPANIONS | 03 / 07 | 四种多选或文本 | 多选完成后 CONFIRM；文本 SUBMIT，接受后显示关键词并前进 |
| BUDGET | 04 / 07 | 三档单选或文本 | 单选 CONFIRM；文本 SUBMIT，接受后显示关键词并前进 |
| CONSTRAINTS | 05 / 07 | 六种多选、No restrictions 或文本 | 多选完 CONFIRM；文本 SUBMIT，接受后前进 |
| FOLLOW_UP_1 | 06 / 07 | 后端问题，单选/多选/文本 | 单选可直接 CONFIRM，多选明确提交；文本 SUBMIT |
| FOLLOW_UP_2 | 07 / 07 | 后端问题，单选/多选/文本 | 同上，成功后进入独立生成状态 |

第 6、7 轮始终占两个固定槽位，不增加第八轮或任意追问链；第七轮依赖第六轮答案生成。Mock 根据先前答案确定问题，不能始终返回同一组硬编码问题。无法生成有效问题时本轮显示错误并可重试，不跳过为完成。

Companions 保留 Solo、Partner、Family、Friends 四种卡片，使用 MULTI_CHOICE、minSelections=1、maxSelections=3。Partner、Family、Friends 可组合；选择 Solo 时取消其他选项，选择其他选项时取消 Solo。页面显示明确 Continue 按钮，不在第一次点击后跳页。客户端和 Mock/后端都必须拒绝 SOLO 与其他类别同时出现的请求。

选中多选标签、点击日历第一天、编辑草稿、纯前后浏览都是本地操作，不逐个请求；发送文字/确认完整答案才请求一次。选项必须有稳定 ID，不能通过 label 猜答案。第 3–7 轮文本若含糊，返回 CLARIFY 并停在当前轮，只有 ACCEPTED 才前进。接受的关键词在 Live Trip 和可回看的本轮摘要中保留，不采用转瞬即逝的提示。

日历沿用已有规则：第一次点击为出发日；第二次更早则重置出发日，等待新的返回日；完整区间再次点选从头开始。日期是 `YYYY-MM-DD`，不是 UTC 午夜时间戳；最多 60 天，包含出发与返回日。灵活日期保持 `mode=FLEXIBLE`，建议日期与用户固定日期必须区分。

## 4. 逻辑接口与版本

Mock 实现以下同等逻辑方法，不启动 HTTP 服务，不需要真实地址。未来 HTTP 路径作为拟定映射：

| 操作 | 拟定路径 | 输入/输出 |
|---|---|---|
| 创建会话 | POST /v1/planning-sessions | CreateSessionRequest → SessionResponse |
| 提交本轮 | POST /v1/planning-sessions/{sessionId}/turns | TurnRequest → SessionResponse |
| 恢复/冲突后刷新 | GET /v1/planning-sessions/{sessionId} | SessionSnapshot；不修改 revision |
| 生成完整行程 | POST /v1/planning-sessions/{sessionId}/itineraries | GenerateRequest → 既有 itinerary JSON |

规划协议 `schemaVersion="1.0"`，与本地 profile v2、最终 itinerary v1 是三个独立版本域。本方案不直接替换现有 `docs/profile/profile.schema.json` 的 recommendationRequest/Response；旧格式继续保留，新增会话协议明确分开，避免后端同学把两者混用。

### 4.1 创建会话

```json
{
  "schemaVersion": "1.0",
  "requestId": "req_create_001",
  "language": "en",
  "profileContext": {
    "age": 25,
    "nationalityCountryCode": "AU",
    "residentialLocation": "Sydney, Australia",
    "tagCatalogVersion": "1.0",
    "travelTagIds": ["ISLANDS", "FOOD", "SLOW_TRAVEL"],
    "travelPreferences": "Prefer quiet neighbourhoods"
  }
}
```

创建成功 revision=1，ORIGIN 进入 AWAITING_INPUT，其他七轮 LOCKED。先建议出发地，用户确认前 origin 不写入已确认答案。language 固定 en；用户自由输入保留原文。

### 4.2 本轮请求：不满意目的地，重新推荐

```json
{
  "schemaVersion": "1.0",
  "requestId": "req_refine_002",
  "sessionId": "session_demo_001",
  "baseRevision": 2,
  "stepId": "DESTINATION",
  "questionId": "question_destination_1",
  "action": "REFINE",
  "input": {
    "type": "TEXT",
    "text": "Somewhere with beaches and less shopping"
  }
}
```

所有上述外层字段必填；`action` 只允许 REFINE / CONFIRM / SUBMIT。REFINE 仅用于 ORIGIN/DESTINATION/DATES；SUBMIT 仅用于第三至七轮；CONFIRM 用于所有轮的明确选择。stepId 固定八种；questionId 必须匹配当前页面问题。requestId、sessionId、questionId、optionId 等 ID 为不透明字符串，1–100 字符，仅允许字母、数字、下划线、连字符；不能作为文件路径。baseRevision 为正整数。请求最多 16 KiB，响应最多 512 KiB（UTF-8 字节），超限明确拒绝，不截断 JSON。

input 是按 type 区分的联合类型，每种只允许所列字段：

| type | 字段 | 用途 |
|---|---|---|
| TEXT | text：1–2000 字符、trim 后非空 | REFINE 或 SUBMIT，不同时传 optionIds |
| OPTIONS | optionIds：1–36 个去重 ID | CONFIRM；每个 ID 必须属于该 question，遵循 min/maxSelections |
| DATE_RANGE | startDate、endDate：有效 ISO 日期字符串 | DATES 的 CONFIRM；完整区间才提交 |

灵活日期由 DATES 的一个明确 OPTIONS 选项表达；No restrictions 也是 CONSTRAINTS 的明确选项，与其他限制互斥，不用空数组含混表示“没回答”还是“没有限制”。CONSTRAINTS 的文本 SUBMIT 是替换当前整份本轮答案；输入框提示这一点，成功前保留原答案，不隐式追加或清空。

### 4.3 本轮响应

统一 SessionResponse 必填字段：schemaVersion、requestId、result、session。result 必填 stepId（创建时 ORIGIN）、status、keywords、navigation、nextStepId。

- status：UPDATED（新推荐，未确认）/ ACCEPTED（答案确认成功）/ CLARIFY（需要澄清）。
- keywords：0–6 个短英文词组，单项最多 60 字符。
- navigation：STAY / NEXT / GENERATE；STAY 时 nextStepId=null，NEXT 指固定下一槽位，GENERATE 时 nextStepId=null。
- REFINE 只能返回 UPDATED/CLARIFY + STAY；不能静默确认城市或日期。
- CONFIRM/SUBMIT 成功返回 ACCEPTED；否则 CLARIFY + STAY。缺字段/非法选项是独立错误响应。

SessionSnapshot 必填字段：id、revision、dataMode、expiresAt、profileContext、steps、canGenerate。

- id=sessionId；revision 为每次成功状态更新后递增的整数；dataMode=MOCK/LIVE，本轮固定 MOCK。
- expiresAt：带 UTC 偏移的 ISO 时间，创建时起 7 天；到期返回 SESSION_EXPIRED，保留本地草稿供新会话重新确认。
- profileContext 是创建时固定上下文；steps 恰好包含八个固定 stepId，数组按上述顺序排列，拒绝缺项、重复、增项。
- 每次返回完整快照，而不是仅返回一句文本或一个 option；前端一次校验、一次原子替换。
- canGenerate 仅在八个答案均 CONFIRMED 且有效时为 true。

每个 StepSnapshot 固定字段：stepId、state、question、answer、keywords。state=LOCKED / AWAITING_INPUT / CONFIRMED / NEEDS_REVIEW。question 与 answer 可空；LOCKED 的 question=null、answer=null；其他状态 question 非空；CONFIRMED 的 answer 非空。未确认的草稿存在客户端，不冒充 answer。

以下是完整响应中的一个 StepSnapshot 示例，不是顶层 HTTP 响应：

```json
{
  "stepId": "DESTINATION",
  "state": "AWAITING_INPUT",
  "question": {
    "id": "question_destination_2",
    "title": "Which escape feels like you?",
    "description": "Choose a destination or tell us what to change.",
    "inputMode": "SINGLE_CHOICE",
    "allowText": true,
    "minSelections": 1,
    "maxSelections": 1,
    "options": [
      {
        "id": "destination_bali",
        "title": "Bali",
        "description": "An island break with a slower pace.",
        "keywords": ["Beaches", "Nature", "Wellness"],
        "imageAssetKey": "city_bali",
        "value": {
          "type": "LOCATION",
          "placeId": "place_bali_id",
          "name": "Bali",
          "countryCode": "ID"
        }
      }
    ]
  },
  "answer": null,
  "keywords": []
}
```

Question 固定字段均见示例；inputMode=SINGLE_CHOICE / MULTI_CHOICE / DATE_RANGE / TEXT，options 始终是数组。单选 min/max=1；多选 1≤min≤max≤36；DATE_RANGE/TEXT 的 min/max=0。DATES 可使用 DATE_RANGE 配合建议区间和灵活日期 options；TEXT 的 options=[]。allowText 控制额外输入框，TEXT 模式必须 true。所有标题/描述为纯文本，禁止 HTML/脚本/任意布局配置；title 最多 120 字符、description 最多 500 字符。

Option 固定字段：id、title、description、keywords、imageAssetKey、value；imageAssetKey 为已注册本地资源 key 或 null，不允许 URL/文件路径。目的地的 keywords 必须恰好三个，其余选项 0–6 个；地名通过 placeId 区分同名城市，不按字符串认定相同地点。选项可在提示中更换 ID/内容，因此重新推荐产生新的 questionId。

各轮问题约束：ORIGIN/DESTINATION 可返回 1–20 个候选；COMPANIONS 固定 4 个；BUDGET 固定 3 个；CONSTRAINTS 固定 6 个限制＋1 个 No restrictions；动态轮若使用选项则为 2–6 个。模型不给颜色、尺寸、View 名字，布局由 App 决定。

### 4.4 规范化答案与选项值

answer 固定字段为 `value` 与 `source`；source=OPTION / TEXT / CALENDAR。value 使用下面的类型，不使用任意对象。除明确可空外，字段必填：

| value.type | 字段 | 适用轮次 |
|---|---|---|
| LOCATION | placeId、name：非空 string；countryCode：两位国家码或 null | ORIGIN、DESTINATION |
| DATES | mode：FIXED/FLEXIBLE；startDate、endDate：日期或 null；notes：string | DATES；FIXED 两日期必填，FLEXIBLE 两者为 null |
| COMPANIONS | kinds：1–4 项去重数组，元素为 SOLO/PARTNER/FAMILY/FRIENDS/CUSTOM；partySize：1–30 integer 或 null；description：string | COMPANIONS；SOLO 必须独占且 partySize=1；其他类别未知人数不能猜成 2 |
| BUDGET | tier：ECONOMY/COMFORT/PREMIUM/CUSTOM；currency：三位币种码或 null；amountMinor：非负 integer 或 null；scope：PER_PERSON_PER_DAY/PER_PERSON_WHOLE_TRIP/ENTIRE_PARTY_WHOLE_TRIP 或 null；notes：string | BUDGET；有金额必须有币种和口径，金额单位为最小币种单位 |
| CONSTRAINTS | items：0–36 个 Constraint；notes：string | CONSTRAINTS；No restrictions 规范化为 items=[]、notes="" |
| FOLLOW_UP | semanticKey：非空稳定 string；values：1–36 个非空 string；notes：string | FOLLOW_UP_1/2；由问题语义解释，不能用题目标题当 key |

Constraint 固定字段：id、label、strength（HARD/SOFT）。初始六种为 VEGETARIAN、WITH_PETS、OLDER_TRAVELLERS、LESS_WALKING、SEE_MORE、RELAXED_PACE；另设 NONE 互斥选项。模型不可静默降级用户明确的硬限制。自由输入产生本轮稳定 CUSTOM ID 与英文 label，用户原文保留于 notes。以上 string 字段除地名/ID外最多 2000 字符，标签最多 120 字符。

Option.value 同样使用这些类型；单个限制选项包含一个 Constraint，NONE 包含空列表。确认多个限制时由会话层合并成一个 CONSTRAINTS answer。确认多个动态选项时，必须具有相同 semanticKey，再合并 values 去重；本次问题不允许混合多个语义。

每个同行人选项的 value.kinds 只有一个元素。多选确认后合并 kinds，固定按 SOLO、PARTNER、FAMILY、FRIENDS、CUSTOM 顺序保存。partySize 表示包含用户在内的总人数，不能按选中标签数相加：Partner 与 Family 可能包含同一个人；未明确人数则为 null，已明确的非 SOLO 人数至少为 2。后端通过动态问题补充所需人数，不把未知自动写成 1。

Companions 自由文本 SUBMIT 替换该轮完整答案，而不是偷偷追加到已选标签。解析出多个类别时全部保留，无法归类的同行关系用 CUSTOM 和 description 表达；矛盾（如同时要求独自出行和带家人）返回 CLARIFY，停留本轮。关键词可同时显示 Partner、Friends 等多个项目；最终 itinerary 的旅客数量不得由 kinds.length 推断。

日期建议是建议，不是已确认答案：日历上可预览，CONFIRM 成功才保存为 answer。模型给出不合法日期、超长区间或无法确定的金额时必须澄清，不强行归一化为有效答案。

## 5. 修改、回退、并发与恢复

1. 浏览前后轮不请求、不改 revision；UI 自己保存正在看的 stepId、输入草稿和滚动位置。不能进入未解锁轮，也不能用前进按钮绕过当前未确认答案。提交某轮前，其前序所有轮必须 CONFIRMED；后端与客户端均检查。当前轮确认后，下一槽位若 LOCKED/NEEDS_REVIEW，则生成问题并进入 AWAITING_INPUT，保留旧答案参考。
2. 用户对已确认轮发起 REFINE 后，该轮进入 AWAITING_INPUT，旧答案可作为可见参考保留，但不再有效；CONFIRM/SUBMIT 成功后才成为新答案。同一轮重复推荐不会追加一轮，也不会改变 01–07 的编号。
3. 保守依赖策略：修改任何已确认轮，其后所有已回答轮标记 NEEDS_REVIEW、保留旧 answer/keywords；未回答轮仍 LOCKED。已失效的下一轮在前一轮重新确认时重新生成 question。旧答案不自动映射为新选项 ID；用户可保留其内容并再次确认，或重新回答。无关的前序答案不受影响。最终生成前必须逐轮重新确认，不把“保留答案”误当“答案仍有效”。
4. 重建问题时生成新 questionId；选项 ID 只对该 question 有效。后端拒绝旧 questionId，即使用户文字相同。动态问题语义变化时展示旧答案为参考，不当作新题答案。
5. 会话同一时刻只允许一个提交；UI loading 禁止重复提交，允许后退浏览。响应仍可更新有效缓存，但只有 requestId、提交来源页面及页面访问标识仍匹配时才能执行 NEXT/GENERATE；用户已后退就不强制跳走。旧 revision 响应不得覆盖新状态。
6. requestId 按一次用户意图生成，超时重试复用同一 ID 与原始请求体；改变输入才新建 ID。后端在会话有效期内保存幂等结果：先检查重放，再检查 baseRevision。同 ID 不同内容返回 IDEMPOTENCY_CONFLICT，同 ID 处理中返回 REQUEST_IN_PROGRESS。成功重放返回相同 revision，不重复确认/生成。未来服务端须原子保存状态、版本和幂等记录。
7. 不同 requestId 的 baseRevision 不匹配返回 REVISION_CONFLICT，不自动覆盖；客户端读取最新快照并保留输入，用户再次提交。请求超时意味着结果未知，不意味着服务端没处理；先同 ID 重试/读取会话，不直接另建一次提交。
8. Mock 会话与草稿使用与档案分离的本机存储；旋转和进程重启恢复会话、版本、八轮快照、草稿与未决 requestId，不只放 Activity Bundle。读到不支持版本显示恢复错误，不删除已有行程。

## 6. 错误 JSON

```json
{
  "schemaVersion": "1.0",
  "requestId": "req_refine_002",
  "error": {
    "code": "MODEL_UNAVAILABLE",
    "message": "We could not update your suggestions. Please try again.",
    "retryable": true,
    "fieldErrors": [],
    "currentRevision": null
  }
}
```

统一成功/错误二选一；不在成功快照中夹带 error。fieldErrors 为 `{field, message}` 数组；currentRevision 只在版本冲突时必填，其余 null。错误不改变已提交状态。message 是安全英文提示，不输出原始模型文本、堆栈或个人资料。

固定 code 与未来 HTTP 对应：INVALID_REQUEST=400、INVALID_ANSWER=422、STALE_QUESTION=409、REVISION_CONFLICT=409、IDEMPOTENCY_CONFLICT=409、REQUEST_IN_PROGRESS=409、SESSION_NOT_FOUND=404、SESSION_EXPIRED=410、INVALID_MODEL_OUTPUT=502、MODEL_UNAVAILABLE=503。REQUEST_IN_PROGRESS、MODEL_UNAVAILABLE、INVALID_MODEL_OUTPUT 的 retryable=true，可按原请求重试。后两种错误保证未提交状态，不缓存为成功的幂等结果，重试时可再次执行；REQUEST_IN_PROGRESS 不允许第二次执行。其余 retryable=false，须修正输入或刷新状态。网络超时是客户端状态，不伪装为已收到上述错误 JSON。

## 7. 最终 itinerary 保持不变

GenerateRequest：

```json
{
  "schemaVersion": "1.0",
  "requestId": "req_generate_001",
  "sessionId": "session_demo_001",
  "baseRevision": 15
}
```

只有 canGenerate=true 才接受；baseRevision 对应完整已确认问卷。返回完整、通过既有 `docs/itinerary-contract/itinerary.schema.json` 的 JSON，走 `SavedTrip.fromContract` 和现有详情/保存链路；不把 SessionResponse 送进 itinerary 解析器。Mock itinerary 明确 dataMode=MOCK，绝不表示已预订。

同 requestId 重试得到同一 itinerary id 与内容；生成期间问卷发生变化，完成结果不得替换当前会话结果。对旧版本生成结果保留引用但不自动保存为“当前行程”，提示基于旧答案，用户显式选择后才保存。最终行程中的 requestSnapshot 从已确认答案组装，地点解析进 places，日期和预算保留明确口径；不能由模型覆盖用户硬条件。

映射边界：origin/destination → 已有地点 ID；DATES → 既有 dateMode/日期字段；COMPANIONS → 已确认人数与旅客信息（未提供年龄等为未知）；BUDGET → 既有预算口径；CONSTRAINTS → 已有 constraints；动态答案与初始偏好作为生成上下文，不把新字段硬塞入严格 itinerary schema。国籍不自动当作持有某国护照，不据此生成已核实的签证结论。

## 8. Mock 实施与交付验收

结构：ProfileStore v2（档案）→ PlanningSessionRepository（持久化/版本/未决请求）→ PlanningService 接口（JSON 输入输出）→ MockPlanningService（规则与受控假响应）。MainActivity 只处理显示和事件，不再同时承担全部业务和假后端。继续使用现有表单组件、日历与图片目录；Java 写清楚状态转换与字段用途的中文注释。

实施按三个可验收增量：

1. 协议：JSON Schema、字段说明、60 标签目录、八轮请求响应示例、错误/回退/重试示例、最终 itinerary 映射。后端同学拿到这些文件即可按同一格式开发。
2. 档案：补全首次输入与标签多选、旧资料安全升级、设置修改；无登录、无网络。
3. 会话：Mock 往返、加载/错误、固定八槽位、动态问题、回退重算、恢复、最终行程生成。

必须验证：结构与语义校验分别执行；60 个标签 ID 唯一且包括 Diving；标签多选可恢复；Companions 多选不推算人数、SOLO 互斥；八轮正常路径；前两轮连续 REFINE 不前进；第三至五轮文本提取关键词；动态题随上下文变化；多选不会第一次点击就跳页；更改出发地；旧问题 ID 被拒绝；回退不增轮数；旧响应不抢页面；依赖答案需复核；失败保留草稿；同 ID 重试只提交一次；生成重试不重复存储；重启恢复未决状态；v1 档案不丢字段；非法模型 JSON 不进入 UI；深色、小屏、大字体与英文文案。

每个 Mock 返回都走与未来真实响应相同的解析、Schema 与业务检查。JSON Schema 不足以验证选项 ID 归属、日期先后、revision 等关系，必须补语义测试。现有 ItineraryShape 是有限子集，不能冒充完整 JSON Schema 引擎；后端测试使用完整实现。

本阶段不修改真实后端、不添加登录、不调用模型、不承诺签证/价格准确性、不改变已有行程 schema、不删除任何用户资料或存储的行程。
