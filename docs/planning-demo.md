# 五步旅行规划演示

## 本版范围

Android Java/XML 本地原型，不连接 AI、推荐、地图、价格或预订接口。保留已有深浅色、胶囊前后按钮和居中的步骤指示。推荐和预算均为假数据。界面文案全部为英语，用户自行输入的内容保留原文。

## 交互与验收

1. **Location**：先选择/输入出发地，再选择/输入目的地。推荐城市各有三个关键词。旅行板依次显示 `Depart from Sydney`、`Sydney → Kyoto`。同城不能继续；选择不会自动跳步。
2. **Dates**：日历第一下是出发，第二下是返回；第二下更早则重设出发并等待返回。可跨月、当天往返；过去日期不能点。选完后再次点击开始新范围。可点“Let us suggest dates”；文字输入只保存时间备注，不解析自然语言。
3. **Companions**：Solo、Couple、Friends、Family 四选一，每项附说明；输入文字可替代预设选项。
4. **Budget**：按目的地提供三档演示预算，单位 AUD/人/天，覆盖住宿、饮食和当地活动，不含机票。自定义城市使用明确标注的通用样例；自填预算原样保存。修改目的地会清空原预算，需重新选择。
5. **Preferences → Demo**：六项可多选，可补充文字，也可无要求。“See more”和“Take it slow”同时选时提示冲突，演示采用较慢节奏。点击生成后出现约 1.2 秒模拟加载，再显示摘要及最多三天的示例预览；长行程会注明仅预览前三天，未定日期显示三天想法。

顶部后退和系统返回均回到上一阶段；第一步后退回首页。生成时后退取消模拟加载并返回第 5 步。选择和未发送草稿保留在当前 Activity 保存状态中，可恢复旋转/系统重建；未完成草稿主动结束应用后不保证恢复。生成完成后，摘要及演示结果会另外持久保存到本机“我的”行程库，相同一次生成在旋转后不重复新增。

## 首页、我的与设置

- 启动进入首页：顶部“Generate my trip”横幅进入五步；下方 Kyoto、Tokyo、Bali 示例可打开详情，但不会自动收藏。
- 底部固定 Home、My Trips、Settings。My Trips 按生成时间倒序显示本机行程，空时显示“No trips yet”；点击可查看当时保存的快照，不会重新生成内容。
- 设置包含账号登录演示说明、本机昵称修改、跟随系统/浅色/深色主题、数据与隐私、关于及读取安装包的版本号。没有真实登录，不保存密码；昵称及主题会本机保存。
- 新增的行程和偏好存储排除在系统云备份及设备迁移中。没有云同步；卸载/清除应用数据会移除本机记录。旧版在此功能加入前未持久化的结果无法自动找回。
- 入口代码是 `HomeActivity.java`；保存快照为 `SavedTrip.java` / `TripStore.java`；示例来自 `ExampleTrips.java`；设置为 `HomeSettings.java` / `AppPreferences.java`。

## 假数据与代码位置

- `Frontend/app/src/main/java/com/example/odyssey/TripOptions.java`：推荐城市、三个关键词、同行人、预算和限制数据。修改该文件可以替换演示内容。
- `Frontend/app/src/main/java/com/example/odyssey/TripPlanState.java`：五步数据、验证和导航。
- `Frontend/app/src/main/java/com/example/odyssey/TripCalendarView.java`、`TripDates.java`：日期展示、范围选择与无时区歧义的日期计算。
- `Frontend/app/src/main/java/com/example/odyssey/PlannerFormRenderer.java`、`MainActivity.java`：卡片、日历、输入框和页面联动。
- `Frontend/app/src/main/java/com/example/odyssey/MockTripGenerator.java`：本地模板生成。自由输入仅保存，不假装调用 AI；具体活动、营业时间、宠物/无障碍适用性未核实，不能用作真实报价或预订依据。

## 本地验证命令

在 `Frontend` 目录执行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

第二条需要启动 Android 模拟器或连接设备。测试覆盖地点校验、日期反选/跨月/闰年、逐步验证、预算失效、模拟生成、返回和 Activity 重建恢复。
