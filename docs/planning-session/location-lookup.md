# 出发地与目的地查询：后端建议

状态：2026-10-29，方案建议；尚未接入数据源或实现地点验证。

## 当前项目已有的基础

后端 `backend/app/tools/weather.py` 的 `geocode()` 已调用 Open-Meteo Geocoding，可返回名称、州/省、国家、坐标、时区及人口。它目前用于天气工具，**未连接问卷的出发地确认流程**；已有代码也没有保留 API 返回的地点 ID 或地点类型。它先取最多 `count` 条，再由客户端过滤逗号后的国家或地区；符合条件的地点可能因被截断而漏掉。只支持一个逗号后的简短提示精确匹配，不能保证识别任意地名表达；不能因为注释声称消歧就认为已彻底解决。

建议第一阶段评估并扩展现有封装，而不是立即从零维护全球城市索引：保留返回的 GeoNames ID 和 feature_code；明确候选与确认步骤；对筛选、空结果、错误做测试。Open-Meteo 官方支持多语言前缀匹配、国家代码过滤和按 ID 查询；名称必须匹配索引开头，不能假设它支持任意错拼。官方 API 文档：[Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api)。

Open-Meteo 地点数据本身基于 GeoNames，因此二者并非独立来源，互相一致不能证明交叉验证通过。免费 API 仅限非商业用途且有速率限制，数据需按条款注明来源；实际使用前检查 [Open-Meteo 使用条款](https://open-meteo.com/en/terms)。

## 推荐：GeoNames 全球地点数据

建议先使用 [GeoNames 数据下载](https://download.geonames.org/export/dump/) 建立后端本地地点索引：`cities500.zip` 包含人口大于 500 的城市及部分行政中心，官方说明约 18.5 万条。它不是所有真实地点的完整清单；小村庄、机场、海岛及热门地区可能缺失。加入 `admin1CodesASCII.txt`、`countryInfo.txt`，让同名结果显示州/省与国家；需要多语言、别名时可额外索引 `alternateNamesV2.zip`。

数据可免费下载、允许商业使用，但遵循 CC BY 4.0，应在产品中注明 GeoNames 来源并链接；官方明确数据不保证准确、及时或完整。数据下载及字段说明见 [GeoNames 数据目录与 Readme](https://download.geonames.org/export/dump/)。记录数据版本及定期更新索引，避免把数据当作永久不变。

如果暂时不搭索引，可先测试 [GeoNames 搜索 API](https://www.geonames.org/export/geonames-search.html)：支持名称、国家、行政区、模糊搜索和结果数量。须注册自己的账号，不要使用官方 demo 账号；截至核对日，免费服务每个应用每天 10,000 credits、每小时 1,000 credits，多数调用消耗 1 credit，详见 [官方服务条款](https://www.geonames.org/export/) 和 [服务文档](https://www.geonames.org/export/web-services.html)。查询超时、额度用完与真正无结果必须区别处理。本地数据方案不依赖这项在线 API 的请求额度。

## 建议流程

1. **查找**：文本做空白、大小写及 Unicode 规范化，查主名称和别名；优先名称精确匹配，再做前缀/拼写相近匹配。用户给出的国家或州用于消歧；居住地只能辅助排序，不能排除其他国家。
2. **列候选**：返回有来源 ID、城市、州/省、国家及坐标的候选，让用户选择。用户输入自然语言时，AI 可以提取可能的地点查询词，但不能生成地名或 ID 冒充查询结果。
3. **处理歧义**：同名、近似拼写或不完整地点都要求用户确认；识别不到时返回 `CLARIFY / STAY` 并请用户补充或更正，不能自动接受。即使只有一个结果，也要明确确认才能继续。
4. **确认**：后端核对用户提交的候选 ID 来自本次有效结果并仍可用，保存来源、来源 ID、展示名称、国家/地区和坐标。输入变更或旧候选失效时应重新查找，不能信任客户端自行提供的地点详情。
5. **失败**：地点服务不可用时显示暂时无法查询，并允许重试；不要报告为“地点不存在”，也不要由模型猜一个结果代替。未收入数据库不代表现实中不存在。

建议先覆盖英文与常用别名，再使用测试集决定是否扩充。目的地可能是岛屿或区域，例如 Bali，不应一律使用城市筛选；机场查询则需要机场数据源。AI 可负责解释和追问，确定地点存在的依据必须来自已查询的数据源。

## 响应结构建议

逐轮对话的通用结构见 [逐轮对话协议](turn-response-contract.md)。下面仅展示需要加入 `result` 的字段；不是当前 Android Mock 可直接解析的完整响应。

```json
{
  "stepId": "ORIGIN",
  "status": "CLARIFY",
  "navigation": "STAY",
  "assistantMessage": {
    "id": "msg_002",
    "role": "ASSISTANT",
    "text": "Which Springfield do you mean?"
  },
  "locationResolution": {
    "status": "AMBIGUOUS",
    "candidates": [
      {
        "source": "geonames",
        "sourceId": "example_1",
        "name": "Springfield",
        "region": "Illinois",
        "countryCode": "US",
        "latitude": 39.78,
        "longitude": -89.65
      },
      {
        "source": "geonames",
        "sourceId": "example_2",
        "name": "Springfield",
        "region": "Massachusetts",
        "countryCode": "US",
        "latitude": 42.10,
        "longitude": -72.59
      }
    ]
  }
}
```

示例 ID 和坐标仅说明数据格式，并非可用的真实 GeoNames 记录。`locationResolution.status` 建议使用 `MATCHED`、`AMBIGUOUS`、`NOT_FOUND`；上游服务失败属于接口错误，不作为 `NOT_FOUND`。`MATCHED` 仍要求用户选择并确认；状态只反映查找结果，并不能证明用户的真实意图。

匹配分数可以用于排序和决定是否追问，但不要把未校准的 AI 自评分数当作事实或承诺真实概率；也不要固定一个未经测试的阈值直接放行。使用含同名城市、错拼、多语言、真实但未收录地点的测试集验证，并关注误接受率和漏查率。

## 备选方案

- [Google Places](https://developers.google.com/maps/documentation/places/web-service/usage-and-billing)：提供更丰富的地点查询，可用于后续扩展；需要启用计费，按服务和用量计价，部署前核对 [最新价格](https://developers.google.com/maps/billing-and-pricing/pricing) 与数据展示/缓存条款。
- OpenStreetMap 公共 Nominatim：其 [官方使用政策](https://operations.osmfoundation.org/policies/nominatim/) 禁止用公共 API 实现自动补全，且限制全应用总速率最多每秒 1 次；因此不建议作为本项目输入时实时搜索的公共服务。自建或第三方服务需另行评估其条款。

## 验收案例

- `Sydney`：返回可确认的城市、州与国家；不直接提交。
- `Springfield`：展示多个同名候选；用户选择后才能提交。
- 不存在的随机字符串：返回 `NOT_FOUND` 并提示修改；不编造城市。
- 轻微拼写错误：给出可能结果并标明需确认，不静默替换。
- 查询超时或额度不足：显示服务暂不可用和重试，与无结果区分。
- 真实小地点不在索引中：明确表示未找到，而不是断言该地点不存在。

## 更可靠的验证与验收

验证分三层，不能互相代替：

1. **数据源能查到**：保留数据源 ID、类型与名称等；地点在数据集里只证明数据源有该记录，不保证它完全准确或用户想去这里。
2. **用户想要这个地点**：显示名称、国家及州/省供用户选择并明确确认；候选无结果、相似拼写、同名和服务失败不得自动跳过确认。对由客户端提交的候选，在后端用关联的搜索结果及来源 ID 重新验证；过期时重新查询。
3. **可用于规划**：检查地点类型、坐标范围、国家/地区字段和业务需求；城市中心坐标不代表机场、车站、住宿或具体道路，交通方式及路线要由后续交通数据验证，不承诺城市间必有航班或道路。

匹配国家/州与原始要求是否冲突；只给候选排序而不把排序分当作概率。不要随意决定数值门槛或伪造置信度；用有人工标注的城市、同名、错拼、多语言、岛屿及不存在输入测试集衡量错误接受、正确候选召回与歧义展示，再根据样本评估风险。住址及国家是排序提示，不能自动当作真实出发地。地理信息提供商也可能返回部分匹配，Google 官方提醒检查 `partial_match` 并比对国家和城市：[geocoding 质量说明](https://developers.google.com/maps/architecture/geocoding-address-validation)。

已有功能的补充验收：确认 Open-Meteo 的 `id` 可透传并按 ID 查回；超过 `count` 的同名候选不会因先截断后过滤而被误判为无结果；模拟服务超时/限额；覆盖确认后回退重选、旧候选过期、双击重试和非法 ID。以上为拟议测试，尚未执行。

若需要更广泛地点类型与成熟的自动补全，可评估 [Google Places Autocomplete](https://developers.google.com/maps/documentation/places/web-service/place-autocomplete) + Place Details；官方支持按城市类型筛选和按地点 ID 获取详情，但需计费、遵守展示/存储条款，且返回结果仍需用户确认。现有 Google Maps MCP 搜索代码服务于景点研究，并不代表已有这个问卷验证流程。两家数据一致时只作为辅助信号；不同供应商也可能使用共同上游数据，不能默认统计独立。
