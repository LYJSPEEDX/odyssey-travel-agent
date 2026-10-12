# 后端模板子任务

用户已批准 docs/itinerary-contract 中的 v1 协议，要求 App 接入并提供“空 JSON 示例让后端大模型返回字段”。你只负责文档与模板，不修改 Frontend、不改既有 schema/示例、不提交 Git。

请先读 docs/itinerary-contract/README.md、itinerary.schema.json、model-output-instructions.md。

交付：
1. docs/itinerary-contract/backend/response.template.json：完整顶层键、常用结构的空占位模板（语法有效但无需符合业务 schema），不可用空字符串冒充真实有效行程；说明填写后必须校验。
2. docs/itinerary-contract/backend/variants.template.json：每种 event 和 mode 的填写形状，包括 FLIGHT/DRIVE/BUS/TRAIN/FERRY/TAXI/WALK；这是辅助模板集合，不是成功响应。
3. docs/itinerary-contract/backend/README.md：中文清晰说明字段/类型/可空规则/枚举/ID关联/时区/整数金额/候选不叠加/LLM和后端职责，附可直接复制的英文提示词。必须指出原协议 schema 是权威、两个完整例子是可测试响应。App 暂不接网络；不要声称已实现具体前端功能。
4. 自查所有模板 JSON 可以解析、键名/对象形状与 schema匹配（占位值除外），输出验证证据。

本任务 UI 文字与 JSON 字段均英语，文档解释中文。不新增依赖，不修改用户文件，不需要网络。使用 apply_patch。

简短报告写 tasks/itinerary-backend-report.md，包含文件、校验命令与结果、风险；返回状态和一行摘要。
