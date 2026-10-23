# 前端适配实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. 按任务实施、测试、审查，不重复询问已批准的产品设计。

**Goal:** 完成可运行的本地资料、可编辑偏好、分轮 Mock 问卷及远程图片展示。

**Architecture:** 继续使用 Java/XML Material 界面。共享文本校验集中到 InputValidation；资料存储、问卷会话、图片加载各自独立；Activity 只协调界面。保留现有 itinerary 渲染和保存入口。

**Tech Stack:** Android Java 11、Material Components、JUnit、AndroidX instrumentation。

**2026-10-23 验收状态：** 三项前端适配及追加视觉修改已实现。完整构建、29 项单元测试、72 项模拟器测试通过；lint 为 0 错误、58 条非阻断提示（主要为未使用资源）。分项审查已进行，最后的隐藏控件测试与首页媒体字段问题由主任务修复并回归验证；独立全量终审因子任务额度耗尽未完成，未将其标记为通过。仍使用 Mock，无真实业务后端连接。

## Global Constraints

- 只改前端，不连接或修改真实后端，不修改后端 schema；之前 protocol-first 计划暂停。
- UI 全部英语，新增 Java 用具体中文注释解释数据流和状态。
- 用户明确要求在 main 实施，保留 Oct-21-backup，不推送远程。
- 现有旅行杂志与登机牌视觉保持，个人页保留已保存行程。
- 文本空值、空白、长度、类型与年龄解析统一到 InputValidation；领域规则保留在相应模型。
- 首次资料包含 name、gender、age、nationality、residential_location、60 个多选 preference tags；个人页可再次编辑。
- 问卷为 ORIGIN 前置步骤加固定七轮，可回退，不改变轮数；Mock 有加载、失败重试与过期响应保护。
- 图片响应中的 HTTPS URL 用于封面/选项，失败保留本地图片；禁止任意本地文件与明文网络访问。
- 问卷选项使用紧凑尺寸；不显示勾选图标，仅标题加粗表示选中，保留多选和无障碍状态。
- 紫色欢迎页的票券轮廓外透明，票身保持白色；保留当前 50% 缩放，不改首页行程卡片边缘。

### Task 1: 资料与共享校验

**Files:** Frontend/app/src/main/java/com/example/odyssey/{InputValidation,PreferenceTags,ProfileStore,RegistrationActivity,HomeActivity}.java，相关 res、Manifest 和测试。
**Interfaces:** InputValidation 提供共享文本与年龄校验。ProfileStore 保留旧 save(int,String,String) 兼容接口，新增完整资料保存；load 返回 JSON，旧 v1 资料可读取并补充新字段，不静默删除。RegistrationActivity 编辑模式使用 EXTRA_EDIT_PROFILE；HomeActivity 的个人/行程页入口重新编辑资料。

- [ ] 添加测试：60 个唯一 tag 含 DIVING；空白/超长/类型错误文本；有效/无效年龄；v1 兼容；资料保存后重载；编辑取消保留旧值。
- [ ] 运行相关测试见失败后实现公共校验、tag 数据、资料 UI 与保存。
- [ ] 首次启动 gate 保留欢迎页；注册 Activity manifest 声明；有完整资料直接进入首页；个人页可进入预填编辑。
- [ ] 测试 assembleDebug、testDebugUnitTest、相关 instrumentation，提交并审查。

### Task 2: 七轮 Mock 问卷

**Files:** 新增 PlanningSession、MockPlanningService、PlanningSessionRenderer 等单一职责类；修改 MainActivity、TripPlanState/适配器、activity_main.xml 与测试。
**Interfaces:** 服务在本地交换结构化 JSON，保持 session/revision/requestId/stepId/action/result/question/options/answer；Activity 不直接伪装成真实网络。既有 TripPlanState 可作为 itinerary 生成适配器。

- [ ] 添加会话测试：origin+7 固定槽位；地点和时间 REFINE 留在当轮、CONFIRM 前进；companions 多选 Solo 互斥；后续文本接受生成关键词；回退编辑标记后续需复核但保留答案；旧请求失效。
- [ ] 实现会话模型和可取消的异步 Mock 服务；失败保留草稿并提供 Retry。
- [ ] UI 连接源地、目的地、日历、人员、预算、限制、两道上下文追问，提供确认、后退与缓存前进；不跳过未确认步骤。
- [ ] 保持最终 itinerary 生成/详情/保存兼容；补充 MainActivity 中文注释；运行单元、构建及完整流程设备测试，提交并审查。

### Task 3: 图片链接适配

**Files:** TripPhotos.java、独立图片加载类、DestinationCards/会话卡片、Manifest 与测试。
**Interfaces:** 读取现有 media READY + coverImageId + url/thumbnailUrl，以及选项 imageUrl；显示本地 fallback 后异步替换，不让旧请求覆盖新绑定。

- [ ] 添加 URL 安全策略和绑定/失败 fallback 测试。
- [ ] 实现有界缓存、超时、取消/绑定保护与 HTTPS 图片加载，复用现有本地城市图片；配置 INTERNET 权限，不接真实业务后端。
- [ ] 让 Mock 示例包含图片链接用于端到端展示，注明图片网络失败不影响流程。
- [ ] 测试构建、lint、设备资料编辑与七轮生成全流程；最终审查。
