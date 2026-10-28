# 当前用户资料：前端 → 后端草案

状态：**供后端评审，尚未联调或发送**。客户端已保存本地资料和 UID，创建 Mock 会话时生成资料快照并提供映射函数；当前逐轮服务仍是 Mock。此前 `docs/profile/profile.schema.json` 和 `docs/planning-session/README.md` 是旧协议，不能直接接收本格式。

## 什么时候发送

建议创建规划会话时发送一次用户资料快照（当前只在本机 Mock 中传递，不会联网），后续每轮发送 sessionId、stepId、选项或消息，不在每轮重复发送完整资料。设置里修改只影响新会话；会话中如需同步更改，双方再确定更新接口。示例是 [完整示例](create-session.example.json) 和 [用户全部跳过示例](create-session.empty.example.json)。路径和外层包装仍需与后端约定，当前 `POST /trips` 并不接受此对象。

## 字段

| 字段 | 类型 | 来源与说明 |
| --- | --- | --- |
| schemaVersion | string | 本传输格式版本，暂为 `1.0`，不是行程 v2 的版本 |
| profile.uid | UUID string | 本地随机生成，首次完成注册保存后稳定；不是账户或认证凭证 |
| displayName | string 或 null | 姓名，可跳过 |
| gender | string 或 null | 所选英文值；不透露为 null |
| birthDate | `YYYY-MM-DD` 或 null | 用户选择的生日；不可推算 |
| legacyAgeYears | integer 或 null | 旧版资料保留的年龄，可能已过时；不根据它推算生日或当前年龄 |
| nationality | string 或 null | 当前界面选择的名称，旧数据可能是其他文本，不能当作国籍代码或护照验证 |
| residentialLocation | string 或 null | 用户选择或定位得到的地名；不等于实际出发地 |
| residenceCountryCode | ISO 3166-1 alpha-2 或 null | 明确识别的居住国家，未识别保持 null |
| displayCurrency | ISO 4217 string | 手动选择优先，其次居住国家建议，未知时默认 USD |
| currencySource | `MANUAL` / `RESIDENCE` / `DEFAULT` | 币种如何确定，默认 USD 不代表用户住在美国 |
| travelTagIds | string[] | 已选择的稳定标签 ID，当前目录 96 项，可为空 |
| travelPreferences | string 或 null | 旧版自由文本，现界面不再提供输入框 |

本机字段没有填写时传 `null`，不传 “Prefer not to tell” 作为真实值。不会发送经纬度、设备标识或定位原始数据。姓名、性别、生日等属于个人资料；正式联网前需要在产品界面说明发送内容及用途，并由后端确定保存期限和访问控制。

## UID 生命周期与限制

- UID 保存在 Android 私有目录的 `profile.json`，首次成功完成资料保存时生成；已完成的旧资料首次读取时补充，修改资料不变。
- 资料损坏或迁移写入失败时不会创建替代档案覆盖原文件。
- 卸载、清除应用数据或更换设备后，本地 UID 会改变；当前资料和 UID 不参与系统备份或设备迁移。将来登录账号需要设计绑定和恢复。
- UID、当前后端 `X-Session-Id`、规划 sessionId、单次请求 requestId 是不同用途；不能仅凭 UID 或 sessionId 做身份认证或授权。
