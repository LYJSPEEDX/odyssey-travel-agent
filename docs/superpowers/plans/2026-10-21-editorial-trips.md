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

- [ ] 先写 EditorialTripsTest：示例卡必须有 ImageView 和目的地；未知城市占位；原始路线与日期可见。
- [ ] 在模拟器运行测试确认旧版缺少封面。
- [ ] 建立受控图片目录，来源记录到 docs/design/editorial-photo-sources.md。
- [ ] 首页品牌栏、荧光绿 banner、示例卡；My Trips 复用相同卡片并保留空状态。
- [ ] 编译、单元/设备测试通过后提交本增量。

## 3. 交互与详情

**文件:** 修改 PlannerFormRenderer.java、MainActivity.java、activity_main.xml、ItineraryRenderer.java、TripDetailActivity.java；新增 DestinationCards.java。

- [ ] 添加目的地选择/Live Trip 缩略图及详情封面回归测试。
- [ ] 目的地照片卡在默认字号双列；小屏、大字体单列；保留自定义输入。
- [ ] 出发地未选不出现图片，目的地确认后更新同一张缩略图。
- [ ] 详情顶部封面；不把城市照片冒充具体餐厅或酒店照片。保留全部时间轴、警告、交通/住宿/预算信息。
- [ ] 检查首页到五步规划、生成到 My Trips、旧版记录、结构化详情。

## 4. 验收

- [ ] assembleDebug、testDebugUnitTest、lintDebug。
- [ ] 新设备测试和原有首页/规划测试；区分已知无关失败与新增回归。
- [ ] 默认屏幕、320dp、大字体、深色模式截图；恢复测试改过的设备配置。
- [ ] 确认 Oct-21-backup 不变，main 有新版提交；交付截图与构建结果。

## 回退

已提交并保持工作区干净时，可切换 Oct-21-backup 查看旧版；不要 reset 或删除用户的行程数据。
