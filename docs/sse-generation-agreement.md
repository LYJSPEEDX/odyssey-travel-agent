# 行程生成 SSE：联调约定草案

状态：已确认采用此交互方向；以下路径和字段仍需前后端落实、联调。现有后端尚未提供这些接口，当前 Android 生成页面使用本地 Mock。

## 1. 创建任务

`POST /generation-jobs` 提交一次完整行程请求，建议使用客户端生成的 `Idempotency-Key` 防止超时重试创建多个任务。成功返回 `202 Accepted`：

```json
{"jobId":"job_123","status":"QUEUED","eventsUrl":"/generation-jobs/job_123/events","statusUrl":"/generation-jobs/job_123"}
```

创建成功后任务应与连接分离：关闭页面或断开 SSE 不等于取消任务。任务如何持久化、有效期、鉴权与取消接口由后端实现并说明。

## 2. 接收进度

`GET /generation-jobs/{jobId}/events` 返回 `Content-Type: text/event-stream`，下面是建议事件格式，`id` 在同一任务内递增：

```text
id: 1
event: progress
data: {"stage":"SEARCHING_FLIGHTS","message":"Finding flights"}

id: 2
event: progress
data: {"stage":"SEARCHING_STAYS","message":"Finding places to stay"}

id: 3
event: completed
data: {"itineraryUrl":"/trips/trip_123/itinerary"}
```

阶段建议：`QUEUED`、`SEARCHING_FLIGHTS`、`SEARCHING_STAYS`、`EXPLORING_DESTINATION`、`BUILDING_ITINERARY`。只发送真正发生的阶段；不能推算完成百分比。示例不保证每次均经过全部阶段。

失败时发送 `event: failed`，`data` 包含稳定的 `code`、面向用户的 `message` 和用于排查的 `requestId`；服务端内部推理、敏感信息和原始日志不发送给用户。连接空闲时可发送 SSE 注释心跳，代理和服务端需允许流式输出。

## 3. 重连与读取结果

客户端保存 `jobId`、最后事件 ID 和现有会话标识；断线后先查询 `GET /generation-jobs/{jobId}`，按后端实际支持使用 `Last-Event-ID` 续接，或以状态查询结果恢复 UI。客户端必须去重事件，不把断线误认成任务失败，更不能自动再次创建任务。服务器需要明确事件可重放的范围和保留期限；如果不支持重放，状态接口必须提供足够的恢复信息。

成功后从结果 URL 获取完整行程。结果版本、字段和 UI 以 [前端 v2 展示需求草案](itinerary-contract/frontend-v2-proposal.md) 最终确认的协议为准，不把 partial/progress 事件当作最终结果保存。

当前 `X-Session-Id` 是匿名会话标识，不等同于登录或安全认证；真正的鉴权、越权保护和后台通知需要单独设计。
