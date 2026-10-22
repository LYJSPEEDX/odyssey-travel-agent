# 逐轮规划协议：评审入口

状态：2026-10-23 设计草案，Mock 已获方向确认，App 与真实后端尚未实现此会话协议。

先读 [完整设计与字段规则](../superpowers/specs/2026-10-23-profile-planning-session-design.md)，再对照：

1. [目的地重新推荐请求](examples/refine-destination.request.json)：用户已确认 Sydney，在目的地轮输入新要求；baseRevision=2。
2. [目的地重新推荐响应](examples/refine-destination.response.json)：revision=3，更新推荐但仍停留目的地轮；包含完整八轮快照。
3. [确认目的地请求](examples/confirm-destination.request.json)：用户选择新推荐中的 Bali，携带新的 questionId 与 revision；成功后才进入日期轮。

这些是可解析的具体演示 JSON，不是空模板、真实用户数据或已通过 Schema 校验的生产响应。日期仅示例，会话生命周期按创建时间计算。全部轮次 JSON Schema、错误/重试实例与 Mock 实现将在设计确认后补齐。

本协议与 `docs/profile/` 的既有早期推荐格式不同，不可直接替换调用；最终完整行程继续使用 `docs/itinerary-contract/itinerary.schema.json`。
