# Native launch ticket implementation plan

> Execute inline in the existing checkout, on `codex/native-launch-ticket`. User approved option A and native UI recreation; no network, data migration or backend changes.

**Goal:** 用原生 Java/XML 重现用户的 Odyssey 登机牌，正常显示 1500 ms 后进入现有首页。

**Architecture:** HomeActivity 保持 launcher，不增加独立 Activity。LaunchWelcome 在内容容器上覆盖票券，结束后移除；LaunchCountdown 只计算前台停留时间并可保存剩余时间。

**Tech Stack:** Java 11, Android Views, ConstraintLayout, XML shape/vector, JUnit, Android instrumentation。

## Constraints

- 白底、荧光绿头部、黑色文本、珊瑚色时间；文案照参考图，属于演示品牌图，不是真实机票。
- 文字使用 TextView；条码是不可扫码的装饰矢量。不得加载整张 PNG 或修改用户原图。
- 票券保持 382:582 比例，完整显示；系统栏与刘海留出安全区。
- 仅 launcher 新进入时显示；返回首页、切换 tab 不重复。旋转保留剩余时间，后台不倒计时。
- 原有浅/深色首页不变；欢迎页固定参考图颜色。系统 Android splash 保留，不伪称替换它。
- Java 增加中文解释；不新增依赖，不提交或推送未经用户要求的 Git 历史。

## Tasks

- [x] 1. 先新增 LaunchCountdownTest，验证 1500 ms、暂停、恢复和剩余时间；运行测试确认缺少该类，再实现 LaunchCountdown 并复测。
- [x] 2. 新增 activity_launch.xml、launch_resources.xml、三个背景 shape 和两个装饰 vector；原生控件按百分比约束布局。新增 LaunchWelcome 管理覆盖层、系统栏和生命周期，HomeActivity 仅转发生命周期。
- [x] 3. 新增 LaunchWelcomeTest 验证 launcher 覆盖层、自动消失、重建/后台、非 launcher 跳过、不同尺寸绘制。已执行构建、单元测试、lint 和设备测试；全量回归例外见下方。

## Verification commands

在 Frontend 中使用 Android Studio 的 JBR：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.example.odyssey.LaunchCountdownTest
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

预期：第一条首次因 LaunchCountdown 尚不存在失败，实现后通过；最终构建、单元测试、设备测试和 lint 无新增错误。截图确认没有位图拉伸、裁切或文字重叠。

## 实际验收（2026-10-21）

- 红灯：LaunchCountdown 尚不存在时编译失败；实现后 4 项计时测试通过。欢迎层接入前 launcher 专项测试按预期失败，接入后通过。
- assembleDebug / assembleDebugAndroidTest 构建通过，全部 20 项单元测试通过；lint 0 errors、24 warnings，新启动页资源无 lint 告警。
- 直接在 API 37 模拟器运行 LaunchWelcomeTest + HomeFlowTest，8/8 通过。旧首页测试增加等待欢迎层结束的步骤，不绕过启动交互。
- 原生布局截图覆盖 320×640、400×850、800×400 dp，以及 fontScale=2。已检查正常竖屏与横屏截图，测试同时验证所有文字边界；图片位于 Frontend/app/build/launch-previews（构建产物，不提交）。
- 全量 25 项设备测试首次有 3 项失败，其中 2 项首页等待问题已修正并专项复测通过；剩余 ItineraryJournalTest.overviewAndDayNotesRemainAccessible 找不到 America/New_York 的断言单独复测仍失败。该行程详情代码与测试不在本次修改范围，未修复，不声明全量通过。
- 欢迎层固定参考图配色；系统设置建议更长的无障碍阅读时间时，实际时长可超过默认 1500 ms。Android 12+ 系统启动画面仍先于此欢迎层。

## 后续修改入口

- 文字/颜色：Frontend/app/src/main/res/values/launch_resources.xml。
- 原生布局：Frontend/app/src/main/res/layout/activity_launch.xml；每块按设计稿比例设置位置与大小，文字自动缩放以防裁切。
- 默认时长：Frontend/app/src/main/java/com/example/odyssey/LaunchWelcome.java 的 DURATION_MS。
- 条码为装饰；名字、航线和时刻保留用户图稿示例，不代表真实机票或飞行时长。

## 用户跟进：系统标识和尺寸

- 欢迎票券通过 activity_launch.xml 的 scaleX/scaleY=0.5，以中心为基点整体缩小至原宽高的一半，保留内部比例，不重新排版。
- HomeActivity 的系统启动主题独立为 Theme.Odyssey.Launch；Android 12+ 使用白底和荧光绿黑色 O 图标，替换模板 Android 标志。系统启动阶段仍存在，并非取消系统 splash。
- onCreate 在 super 之前恢复 Theme.Odyssey，避免启动白底主题影响原首页的深色设置。
- 桌面 launcher 图标未修改；新增测试校验系统启动主题的图标引用以及票券的两个缩放系数。
- 跟进验证：改动前两个新增断言按预期失败（缩放仍为 1、品牌图标不存在）。改动后 assembleDebug / assembleDebugAndroidTest 构建通过，20 项单元测试通过，lint 0 errors / 24 warnings。
- 跟进设备验证未完成：直接运行 9 项设备测试期间模拟器断开（adb devices 返回空列表）；不能沿用上一版 8/8 结果宣称本版设备测试全部通过，半尺寸的新截图也尚未取回。

## 用户最终指定：系统图标也用登机牌

- ic_odyssey_splash.xml 已由 O 标识改为登机牌矢量：绿色票头、Odyssey、SYD/HKG、时间、姓名、登机信息与装饰条码。文字转换为路径，不依赖设备字体，也不是放大原始 PNG。
- 图标采用 108×108 viewport；382×582 原稿缩放为 38.2×58.2 后居中，外接圆半径约 34.81，小于系统中心安全圆半径 36，给圆形遮罩保留余量。
- 系统启动图标是静态矢量，与后续欢迎页的可编辑 TextView 独立；两者共享颜色资源。欢迎页 50% 缩放与 1500 ms 逻辑不变。
- 已检查生成的矢量预览；当前没有连接模拟器，尚未复核系统实际启动截图。预览在 Frontend/app/build/boarding-pass-splash-preview.png，不提交构建产物。
