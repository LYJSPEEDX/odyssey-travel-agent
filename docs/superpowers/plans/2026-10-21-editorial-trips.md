# 旅行杂志＋登机牌 UI 实施计划

> 本轮按已确认的交互预览在当前会话执行；用户明确要求在 main 修改。

**Goal:** 将照片封面、登机牌信息区和目的地图片选择落到原生 Android 页面。

**Architecture:** 继续使用 Java/XML/Material Views。共用照片目录与卡片组件，状态仍由现有 TripPlanState、TripStore 和 ItineraryDocument 管理。

**Tech Stack:** Java 11、Android Views、Material Components；不新增网络权限或第三方依赖。

## 全局约束

- 英文 UI、中文教学式 Java 注释；不改后端及最终 JSON schema。
- 保留五步状态机、日历规则、生成和本地保存、深色模式、欢迎页。
- 本轮照片打包离线使用。未知城市显示明确占位，不随机匹配其他城市。
- 不执行模型提供的路径或 URL。media.assetKey 仅匹配明确目录；网络图片为后续独立工作。
- 卡片城市路线来自数据，不把驾车/公交线路画成拟定航班；时间、价格不伪造。
- 普通卡片整张可点，独立动作触控区至少 48dp；大字体/小屏目的地改单列。

## 1. 旧版备份（已完成）

- [x] 基线 assembleDebug/testDebugUnitTest 通过。
- [x] Oct-21-backup 快照：15161c2，包含原有未提交源码。
- [x] main 快进至同一快照；不推送远程。

## 2. 照片与封面卡片

**文件:** 新增 TripPhotos.java、TripCardView.java、item_editorial_trip.xml、照片与相应资源；修改 HomeActivity.java、home_banner.xml、ExampleTrips.java。

- [x] 先写 EditorialTripsTest：示例卡必须有 ImageView 和目的地；未知城市占位；原始路线与日期可见。
- [x] 在模拟器运行测试确认旧版缺少封面。
- [x] 建立受控图片目录，来源记录到 docs/design/editorial-photo-sources.md。
- [x] 首页品牌栏、荧光绿 banner、示例卡；My Trips 复用相同卡片并保留空状态。
- [x] 编译、单元/设备测试通过后提交本增量：a40c1ca。

## 3. 交互与详情

**文件:** 修改 PlannerFormRenderer.java、MainActivity.java、activity_main.xml、ItineraryRenderer.java、TripDetailActivity.java；新增 DestinationCards.java。

- [x] 添加目的地选择/Live Trip 缩略图及详情封面回归测试。
- [x] 目的地照片卡在默认字号双列；小屏、大字体单列；保留自定义输入。
- [x] 出发地未选不出现图片，目的地确认后更新同一张缩略图。
- [x] 详情顶部封面；不把城市照片冒充具体餐厅或酒店照片。保留全部时间轴、警告、交通/住宿/预算信息。
- [x] 检查首页到五步规划、生成到 My Trips、旧版记录、结构化详情。

## 4. 验收

- [x] assembleDebug、testDebugUnitTest、lintDebug。
- [x] 新设备测试和原有首页/规划测试；区分已知无关失败与新增回归。
- [x] 默认屏幕、320dp、大字体、深色模式截图；使用局部 Configuration，没有修改设备全局设置。
- [x] Oct-21-backup 保持 15161c2，main 有新版提交；截图位于 Frontend/app/build/editorial-previews/。

## 回退

已提交并保持工作区干净时，可切换 Oct-21-backup 查看旧版；不要 reset 或删除用户的行程数据。

## 补充行为

- 首页书签会将示例完整保存到 My Trips；相同 ID 已存在则保留已有记录，不覆盖或重复生成。
- 收藏只是本地保存，不代表预订，也不提供取消收藏/删除动作。
- 结构化示例入口 ID 与 contract.id 不同，保存通过 SavedTrip.fromContract 重新解析，确保重启后可读取。
- 图片共六张，约 1 MB；未接后端、图片搜索、远程图片下载或登录。

## 验证记录

- 构建成功，20 项 JVM 单元测试通过；Lint 为 0 错误、39 警告（依赖更新、未使用资源等），未借此升级依赖或删除旧资源。
- 设备测试按批次覆盖 EditorialTripsTest（6）、EditorialLayoutTest（2）、HomeFlowTest（4）、PlannerFlowTest（4），共 16 项。
- 最后联合运行先得到 15/16；五步生成测试固定等待 1.8 秒，未覆盖 Handler 排队＋后台保存的总耗时。日志出现明显模拟器丢帧。测试改为最多 10 秒等待实际保存成功，再检查条目数、内容和重建不重复，不修改生成/保存业务逻辑。
- 修正等待方式及大字体勾选图标后，重跑 PlannerFlowTest＋EditorialLayoutTest，6/6 通过；同次 assembleDebug、testDebugUnitTest、lintDebug 成功。
- 检查真实设备截图中的封面圆角、票据缺口与 Live Trip；离屏布局测试覆盖 320/400dp × 100%/200% 字号 × 浅/深色八种配置。
- 本轮为相关回归测试，不宣称整个项目所有设备测试通过；尚未进行物理真机性能验收。
- 只读代码审查未发现阻断问题；没有远程推送。
