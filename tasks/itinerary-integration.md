# JSON 行程接入计划

目标：现有 Android Java/XML App 可以读取 v1 JSON 并展示行程；后端获得空模板与明确说明。

约束：所有 UI 英语；不接网络/模型/图片服务；保留旧 schema=1 本机行程；不删除用户数据；不自动提交；继续现有配色。模型数据只作文本，禁止执行链接/代码；解析有长度和引用检查。完整事实核实仍由后端负责。

## 任务

- [x] 解析与资产：`ItineraryDocument.parse(Context,String)` 返回快照和 ID 索引；`ItineraryAssets.example(Context,String)` 白名单加载 docs 示例。拒绝版本、未知事件类型、缺字段、断引用及超限输入。
- [x] UI：`ItineraryRenderer` 画头部、日选择器、事件、交通候选、住宿、费用和提示；日选择由 Activity 保存。10 天按天渲染；图片使用明确占位，不虚构地图/照片。候选仅比较，不假重排。
- [x] 生成与存储：`MockItinerary.fromPlan` 创建协议内 PARTIAL 草稿，保留真实用户选择。SavedTrip 写 schema=2、读 schema=1/2；`fromContract` 是后续真实响应的接入入口。生成后可打开详情，旧结果保留。
- [x] 后端交接：已创建空响应模板、5 事件/7 交通变体、中文字段指南及英文提示词，明确空模板不能直接当成功响应。
- [x] 验收：3/10 天每一天、跨日、图片占位、旧/新存储、重建、生成回归、异常输入均覆盖；构建、lint、原示例与模板验证通过。未接接口和账号。

## 验证命令

在 Frontend 执行 `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`。
测试代码位于 `Frontend/app/src/androidTest/java/com/example/odyssey/ItineraryIntegrationTest.java`。
失败样例须拒绝而不是静默补默认值；读取错误显示英文提示。长期后端需完整 JSON Schema 和语义验证，Android 防御检查不替代后端校验。

## 进度

完成：2026-10-12。最终构建成功，13 单元测试 + 18 设备测试通过，lint 0 errors / 26 warnings；Schema + 2 示例 + 8 反例检查通过；两个模板形状检查通过。

目视验收：将最终 APK 安装到 Medium_Phone，打开首页和 New York 示例详情，确认英文文案、配色、圆角、警告卡、Day tabs 与交通时间信息正常显示。截图保存在临时目录，仅用于本轮检查。

测试过程：测试文件先于实现加入；首轮模拟器未就绪，未得到有效红灯结果。设备就绪后暴露 start/end 字段映射错误，修复后通过。60 天输入限制取得红灯后实现并通过。审查发现并修复两套预览不一致及解析前深度保护问题，补充 3000 层嵌套和宽松单引号语法反例。

实现边界见 docs/itinerary-contract/android-integration.md。没有执行 Git 提交；工作期间检测到外部将分支切回 main 并创建 frontend 提交，保留该状态，没有重置或移动分支。
