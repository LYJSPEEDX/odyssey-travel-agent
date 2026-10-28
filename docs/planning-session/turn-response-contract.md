# 规划对话：逐轮返回格式（前后端对齐草案）

状态：2026-10-29。本文是建议接口，不代表后端已实现或双方已联调。现有 Android 使用本地 Mock；`PlanningJson` 解析的是完整会话快照，不能直接解析下方精简示例。本文中的 `schemaVersion` 是逐轮对话协议版本，与行程结果的 v2 schema 无关。

## 1. 顺序与展示内容

页面按固定顺序保存 8 个步骤。返回的 `stepId` 标识刚处理的步骤；`nextStepId` 标识接下来显示的步骤。

| 步骤 | stepId | 页面上方需要的数据 |
|---|---|---|
| 0.5 出发地 | ORIGIN | 出发城市选项；确认后 Live Trip 显示出发城市 |
| 1 目的地 | DESTINATION | 城市选项；每个城市名、3 个关键词、可选图片 URL；确认后显示出发地 → 目的地 |
| 2 时间 | DATES | 日期区间或灵活日期；提供日历及文字提示 |
| 3 同行人 | COMPANIONS | 可多选选项，每项标题和一句说明；Solo 与其他选项互斥 |
| 4 预算 | BUDGET | 根据目的地提供 3 档，每项标题和说明；有金额时同时提供币种及数额依据 |
| 5 限制 | CONSTRAINTS | 多选选项，每项标题和说明；无特殊限制与其他选项互斥 |
| 6、7 补充问题 | FOLLOW_UP_1、FOLLOW_UP_2 | 根据之前答案生成的问题、选项和说明，也允许文字输入 |

每步都可以展示 `assistantMessage.text`，内容放在页面下方 AI 气泡；`question.description` 是上方题目说明，两者用途不同。图片取不到时前端显示占位图，不影响操作。

## 2. 建议每次成功返回的结构

下面是**完整的精简协议示例**，展示用户在目的地页面输入要求后，AI 回复并更新推荐。它是建议的新传输格式，不是当前 Mock 的实际输出；接入时需同时改前端解析器和后端。

```json
{
  "schemaVersion": 1,
  "requestId": "req_002",
  "sessionId": "session_001",
  "revision": 3,
  "result": {
    "stepId": "DESTINATION",
    "status": "UPDATED",
    "navigation": "STAY",
    "nextStepId": null,
    "keywords": ["Beaches", "Relaxed pace"],
    "assistantMessage": {
      "id": "msg_002",
      "role": "ASSISTANT",
      "text": "I found some relaxed beach destinations. Choose one and confirm to continue."
    }
  },
  "currentStep": {
    "stepId": "DESTINATION",
    "state": "AWAITING_INPUT",
    "question": {
      "id": "question_destination_2",
      "title": "Where would you like to go?",
      "description": "Choose a destination or tell me what you prefer.",
      "inputMode": "OPTIONS",
      "allowText": true,
      "minSelections": 1,
      "maxSelections": 1,
      "options": [
        {
          "id": "destination_bali",
          "title": "Bali",
          "description": "An island escape at a slower pace.",
          "value": "Bali",
          "keywords": ["Beaches", "Nature", "Wellness"],
          "imageUrl": "https://example.com/bali.jpg"
        }
      ]
    },
    "selected": [],
    "answer": null
  }
}
```

`requestId` 与请求一致，用于匹配返回；`sessionId` 标识一次规划；`revision` 是服务端更新后的会话版本；`currentStep` 是响应后应显示的页面。`result.stepId` 始终指刚处理的页面，即使 `navigation` 为 `NEXT`，`currentStep.stepId` 也会变成下一页。`assistantMessage` 可为 `null`，如果用户在聊天框发送消息，则成功响应必须包含非空的 `text`。消息 ID 应稳定且唯一，以避免重试时重复显示。示例 URL 仅为占位，不是真实图片。

当前 Mock 使用 `result` 加 `session`（含固定 8 步完整快照），聊天内容位于每个步骤的 `messages` 数组中，元素为 `{"role":"ASSISTANT","text":"..."}`；没有独立 `assistantMessage` 或消息 ID。示例中的顶层 `sessionId`、`revision`、`currentStep` 是建议字段，不是现有 Mock 的原样输出。现有 Mock 的 `answer` 为字符串，样例中的 `null` 也需要适配。不要把本草案直接交给当前解析器使用。

## 3. 不同动作怎么返回

| 用户动作 | 请求 action | 返回 status / navigation | AI 文字和页面 |
|---|---|---|---|
| 前三步输入文字，要求调整建议 | REFINE | UPDATED / STAY | 返回 AI 回复；按需要更新 `currentStep.question` 的选项。用户仍需选择并确认 |
| 下方聊天框继续提问 | CHAT | UPDATED / STAY | 返回 AI 回复；默认不确认答案，也不自动跳页。必要时可更新上方选项 |
| 点击选项后确认 | CONFIRM | ACCEPTED / NEXT；最后一步为 GENERATE | 保存答案，`currentStep` 返回下一步；最后一步可返回 `null` |
| 文字作为第 3 步及之后的答案并确认 | SUBMIT | ACCEPTED / NEXT；最后一步为 GENERATE | 提取关键词并保存原文及规范化答案；返回下一步。聊天本身不等于提交 |
| 信息不足 | 任意动作 | CLARIFY / STAY | AI 说明缺什么；保留当前内容，等待补充 |

目的地与时间由用户明确确认后才前进。确认并进入下一步时，`result.assistantMessage` 如非空，属于 `result.stepId` 指向的旧步骤；下一个问题的说明放在 `currentStep.question`。日期可选择灵活；若选择固定日期，建议正式接口使用 `YYYY-MM-DD`，用户选到早于出发日的日期时由日历重新作为出发日。当前 Mock 使用整数 `YYYYMMDD` 和 0 表示未选择，正式格式需修改前端和后端后才生效。

## 4. 给后端的最小规则

1. **创建会话**：发送本地用户 UID 和资料快照，字段见 [用户资料格式](../profile/README.md)。UID 是本地标识，不代表登录或身份认证。
2. **逐轮请求**：沿用现有请求名称 `requestId`、`sessionId`、`baseRevision`、`stepId`、`questionId`、`action`、`input`；`input` 按文字、选项或日期区间区分。后端按请求版本处理，过期时返回明确冲突错误，不覆盖较新答案。
3. **重新进入旧步骤**：允许修改；已确认的下游答案保留，但因上游变化可能不再适用时标记需要复核，不静默删除，也不自动认定仍有效。
4. **结果和错误**：成功返回结构化内容，模型回复只作为 `assistantMessage.text`；失败返回稳定错误码与可读提示，例如 `REVISION_CONFLICT`、`INVALID_INPUT`、`TEMPORARY_ERROR`，不要把错误伪装成成功的 AI 回复。重试同一个 `requestId` 不应重复应用答案或重复插入消息。
5. **生成阶段**：最后一步确认后启动行程生成，使用另外的 SSE 事件约定，见 [生成 SSE 约定](../sse-generation-agreement.md)。逐轮聊天和最终行程生成是两个不同流程。

待双方确认：精简返回还是保留完整快照、会话恢复/聊天历史接口、规范化答案结构、失败 HTTP 状态码，以及哪些字段允许为空。真实端点、SSE 和 AI 尚未由这份文档实现。

地点数据源、消歧和查询失败处理的具体建议见 [出发地与目的地查询](location-lookup.md)。

## 当前阶段：AI 不确定时追问（2026-10-29）

用户选择本阶段先用 AI 判断，不引入新地点 API。真实模型接口尚未接入；Android 目前使用**本地演示规则**模拟 `CLARIFY / STAY`，不产生真正的 AI 置信度，也不证明城市存在。演示城市列表只用于测试交互，其他真实地点也可能被要求补充；需要接入真实模型后再评估效果。

未来真实后端对输入返回理解程度及可读追问；不确定时返回 `status: "CLARIFY"`、`navigation: "STAY"`，并在当前步骤消息中提供 AI 文字，不确认、不自动进入下一步。明确返回的选项仍需用户确认。预算缺币种或总额/人均时应追问；日期和人数等可确定的数据还应做规则校验。模型的分数并非客观真实性保证：地名、航班等事实即便评分高也可能出错，使用前需评估数据和行为，不可承诺已完成地点验证。

现在前端演示：城市只识别少量示例，时间支持日历和部分灵活日期表达；自由文字只拦截明显缺乏内容的输入及纯数字预算，其余文字并未做语义校验。真实模型、分数阈值、追问后结合多轮上下文与后端持久化都尚未实现。[地点数据源研究](location-lookup.md) 保留作未来参考，本阶段不接入。
