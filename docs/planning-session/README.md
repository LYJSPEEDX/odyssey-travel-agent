# 逐轮规划协议：评审入口

状态：2026-10-23 用户已确认设计（60 个旅行/运动标签、Companions 多选）；待后端负责人联调评审。App 与真实后端尚未实现此会话协议。

先读 [完整设计与字段规则](../superpowers/specs/2026-10-23-profile-planning-session-design.md)，再对照：

1. [目的地重新推荐请求](examples/refine-destination.request.json)：用户已确认 Sydney，在目的地轮输入新要求；baseRevision=2。
2. [目的地重新推荐响应](examples/refine-destination.response.json)：revision=3，更新推荐但仍停留目的地轮；包含完整八轮快照。
3. [确认目的地请求](examples/confirm-destination.request.json)：用户选择新推荐中的 Bali，携带新的 questionId 与 revision；成功后才进入日期轮。
4. [同行人多选请求](examples/confirm-companions.request.json) 与 [规范化答案](examples/companions.answer.json)：Partner＋Friends 同时选择，人数未知为 null，不能根据标签数量推算人数。

这些是可解析的具体演示 JSON，不是空模板、真实用户数据或已通过 Schema 校验的生产响应。日期仅示例，会话生命周期按创建时间计算。全部轮次 JSON Schema、错误/重试实例与 Mock 实现将在设计确认后补齐。

本协议与 `docs/profile/` 的既有早期推荐格式不同，不可直接替换调用；最终完整行程继续使用 `docs/itinerary-contract/itinerary.schema.json`。

标签目录为 60 项，profile 的 travel_tag_ids 上限同步为 60；问卷选项数组的通用上限仍为 36，不与用户档案标签数量混用。同行人 value 使用 kinds 数组，不再使用单值 kind。Solo 与其他选项互斥，多选完成后才提交。
