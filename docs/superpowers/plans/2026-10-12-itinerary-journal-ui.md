# Travel Journal UI Implementation Plan

> **For agentic workers:** Use executing-plans for the coupled UI work, with read-only review before handoff. Existing dirty changes must be preserved; no automatic commits.

**Goal:** 将 JSON 行程详情变成轻量旅行手账，第一屏尽早出现当天内容。

**Architecture:** 保留 ItineraryDocument、Schema 和 TripStore。新增 JournalUi 处理展示组件、JournalTime 处理阅读用日期，ItineraryRenderer 保留数据映射。TripDetailActivity 管理日期和本页 AI 调整草稿。

**Tech Stack:** Java/XML, existing Material Components, minSdk 24. No new dependency.

## Global Constraints

- 所有界面文案英语；保留奶油底、黑字、荧光绿主操作，兼容深色。
- 不接模型/图片/地图接口；没有图片时使用紧凑文字/图标，不用假照片或大空白照片卡。
- 不修改协议、价格、候选选择和旧记录；详情弹层保留完整数据及风险。
- AI 入口仅收集当前页面的本地调整草稿，明确 Not sent；不冒充行程已调整。
- 控件触摸目标至少 48dp，时间轴在窄屏及大字体下可读。

## Tasks

- [x] 先增加 ItineraryJournalTest：页面应有 journal-header、timeline-row、hotel-carousel、journal-adjust；本地草稿重建后保留；运行设备测试并确认新增标记缺失导致失败。草稿路径不写 contract 或 TripStore。
- [x] 顶部改为紧凑目的地/日期/行程概览和小 DEMO 标签，风险改紧凑摘要；新增 JournalTime 和 JournalUi；运行构建与目标测试。
- [x] 时间轴左侧时间/线，航班票券卡，地面交通小行，活动/餐饮摘要，酒店横向候选；原完整正文通过详情入口查看。保留全部日选择与跨日关联。
- [x] 底部 Ask AI to adjust 固定入口；引导按钮填入输入框，保存草稿到 Activity Bundle；关闭弹层不丢输入；明确离线、不发送、不改行程。
- [x] 单元/设备回归、lint、浅色/深色/窄屏/大字体目视检查；记录结果与边界。

## Validation

`cd Frontend && ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`

先单独运行 `-Pandroid.testInstrumentationRunnerArguments.class=com.example.odyssey.ItineraryJournalTest` 验证红灯。目标测试实际点击弹层和引导按钮，检查 UI 状态而不是仅检查函数存在。已有 ItineraryIntegrationTest 必须继续通过，3/10 天每一天均可显示。

## Progress

设计已获用户确认；在 codex/itinerary-journal-ui 原目录继续，保留上轮未提交修改，不复制或清理工作区。

独立只读审查发现概览/当天说明入口缺失与日期读屏描述不完整；已补回并增加红→绿设备回归。320dp、fontScale=2 的实际截图发现横排裁切，已改上下排列并目视复查。模拟器尺寸、字号和深色设置均恢复原值。

最终验证：assembleDebug 成功；16 项单元测试、21 项设备测试通过；lint 为 0 errors / 27 warnings（依赖更新提示、重复背景、未使用资源与一处日期按钮文案拼接）。默认尺寸浅/深色、320dp+两倍字体、酒店横向卡片均已目视检查。不承诺所有设备已验证；未接真实 AI、图片或地图。未提交或推送。
