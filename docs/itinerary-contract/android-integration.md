# Current integration status

Backend generation now emits the shared frontend v2 contract. Android's `SavedTrip.fromContract`
accepts it and preserves v1 reading. HTTP integration now uses a separate live planner with persisted sessions, explicit proposal confirmation, generation and server-trip recovery. Generation jobs/SSE remain pending. See [live demo setup](../live-android-demo.md).
See [v2](v2.md) for null/unknown states, date and cost semantics, fixture verification and legacy handling.
The historical walkthrough below may describe earlier UI limitations; the code and v2 schema are authoritative.

# Android 接入说明

当前状态：本地 JSON → 校验 → 英文行程详情 → 本机保存已接入；不包含 HTTP、真实模型、图片下载、地图或预订。

## 1. 在 App 中查看

首页的 Sydney → Canberra 与 Sydney → New York 分别读取完整的 3 天和 10 天 JSON 示例。原有示例保留。完成五步表单后，点击 Open itinerary 打开完整天数的本地草稿；它保留用户选择，未知交通、住宿、价格显示待确认。旧文本行程仍可从 My Trips 打开，不自动迁移或覆盖。

详情采用旅行手账时间轴，按天切换；跨日事件引用原交通事件。航班以票券卡展示当地日期、时间与时区，地面交通用紧凑连接卡。交通候选默认折叠，点击展开比较；住宿横向展示候选及是否计入当前计划。预算包含当前方案明细、预备金和排除项。Overview 与当天日期按钮保留概览和当天说明；货币、签证、建议与来源可展开。所有样例仍为 MOCK，不代表可出行或可预订。未接入图片加载，不显示大块照片占位；坐标以文字显示，不绘制假地图。

底部 Ask AI to adjust 打开离线草稿弹层，提供 Less walking、Lower budget、More free time 引导按钮。草稿仅当前详情页保留，屏幕重建后恢复，离开该行程后清除；不会发送模型请求，也不会修改或重新保存行程。窄屏或大字体下，时间与航班端点改为上下排列，避免裁切。

## 2. 给后端哪些文件

将 `backend/response.template.json`、`backend/variants.template.json`、`itinerary.schema.json`、`model-output-instructions.md` 与用户请求一起提供给模型；字段解释和复制用提示词在 `backend/README.md`。空模板仅用于填写，不能直接交给 App。两个 examples 是可运行的完整假数据。

构建任务 `syncItineraryAssets` 从本目录复制 Schema 和 examples 到 App 生成资源，因此修改示例后重新构建即可，不用维护第二份 JSON。复制范围不包含模板、说明或校验脚本。

## 3. 后续真实接口入口

Java 入口为 `SavedTrip.fromContract(context, responseJson)`：解析 v1 并创建保存快照。后台线程调用 `new TripStore(context).save(snapshot)`，确认返回 true 后，使用 `TripDetailActivity.EXTRA_ID` 打开对应记录。详情页内部由 `ItineraryDocument` 建立 ID 索引、`ItineraryRenderer` 按事件类型绘制。

目前没有接口请求代码。接真实接口时，还需处理认证、超时、错误响应与 revision 冲突；不能把分段流式 JSON 或失败响应当成完整行程。保存前应在后端验证完整 Schema、语义和真实来源。不要在多个并发生成任务中直接覆盖较新的 revision。

## 4. 数据与安全边界

本机保存格式 `schema=2` 包含完整 `contract`，协议自己的 `schemaVersion` 仍是 `1.0`；旧 `schema=1` 保留原文本。记录按各自 ID 保存，不清空整个库；损坏或不支持的数据显示读取错误，不擅自删除。

Android 做长度/解析前深度限制、当前 Schema 的形状/枚举/必填检查、ID 唯一和引用检查、天数检查；不是完整通用 JSON Schema 引擎，不验证 format、航线可行性、时区偏移、价格运算或签证正确性。真实接口必须在后端完成这些校验。App 不执行模型提供的 HTML、代码或 URL。当前只展示后端汇总金额，不自行将所有候选加总。

本地生成草稿明确标记 PARTIAL：人数尚未收集，用一个资料未填写的旅客占位；城市未查询，国家代码 ZZ、Etc/UTC 仅作草稿占位，不能当作目的地实际时间；灵活日期暂建议一周后的三天。默认显示 AUD，不是假汇率。所有价格未查时，已知费用小计为零，但 UI 显示 Estimate pending，不显示为免费旅程。真实模型响应不能照抄这些占位事实。

协议与表单最多支持 60 天，超过上限必须重新选择，不静默截断。候选仅比较，不提供没有重算时间和预算的假切换。
