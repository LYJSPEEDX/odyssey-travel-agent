# 行程生成页：简要说明

## 用户看到什么

1. 生成时展示路线、日期、同行人和预算；下面是不断变化的状态卡片。
2. 使用平缓的动态进度指示，不显示猜测的百分比；140 秒只是本地演示时长。
3. 可以展开行程摘要、查看已出现的状态、演示断线及重连；完成后打开本机 Mock 行程。
4. 标注 **DEMO**：所有阶段和时间都是本地模拟，不代表后端正在工作。

## 未来 SSE 约定（建议，尚未实现）

- 提交生成请求后获得 jobId；用 `GET /generation-jobs/{id}/events` 订阅状态。
- 事件包含事件 ID、类型和数据；初步类型：`progress`、`completed`、`failed`。
- 断线后查询任务状态，再按后端支持的事件 ID 恢复；不能重新提交生成请求制造重复行程。
- 后端必须保证任务与连接分离，客户端退出或关闭 SSE 时任务仍可查询；实际生命周期、权限、去重策略需双方确定。
- App 在后台或被系统结束时 SSE 不保证常连。Mock 不代表后台任务或通知已经实现。

## v2 Schema 是什么

Schema 是一份 JSON 规则文件，规定 v2 行程的字段、类型、必填项、枚举、可空值和基本格式；它不是一次生成的行程数据，也不是模型的自然语言提示词。

例如（仅说明写法，不是完整正式 Schema）：

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["schemaVersion", "id", "days"],
  "properties": {
    "schemaVersion": {"const": "2.0"},
    "id": {"type": "string", "minLength": 1},
    "days": {"type": "array", "items": {"type": "object"}}
  }
}
```

前端先确定完整字段及校验：见 [前端 v2 Schema](itinerary-contract/frontend-itinerary-v2.schema.json)。当前后端的另一种 v2 与此不同，需要后续进行协议对齐。跨字段关系（例如候选 ID 必须存在、金额合计正确）还需要额外逻辑校验，不能只靠这个简化示例。

Android 支持前端 v2 及既有 v1；生成的数据仍是本地 Mock。后端的 v2 不能直接使用前端解析器，真实对接前需要协议映射和联调。

## 已确认的 SSE 方向

双方采用「创建任务 → 订阅 SSE → 查询任务」的方向；本次仅完成前端 Mock，URL、错误码、事件保留时间与任务取消行为仍需后端落地并联调。生成过程的状态不能把后台内部模型推理或日志原文直接展示给用户。

完整事件草案见 [SSE 联调约定](sse-generation-agreement.md)。

v2 以前端展示内容为起点，见 [前端 v2 说明](itinerary-contract/frontend-v2-proposal.md)；它尚未替代后端现有格式。

## 每个问卷步骤的对话

每一步在原有选择控件下方保留独立的用户／AI 对话区域；返回某一步可继续查看该步记录。前两轮城市及日期的文字仍用于刷新本地建议；后续轮次的文字先用于演示对话，点击 “Use as answer” 才确认为回答。当前回复为 Mock，不代表真实模型。真正的逐轮会话需要后端单独提供接口，尚未由生成 SSE 方案覆盖。
