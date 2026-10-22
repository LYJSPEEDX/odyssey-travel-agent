# 偏好标签与同行人协议增量 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的 60 个标签与同行人多选规则做成独立可验证的 JSON 数据和协议，供随后首次资料页与 Mock 会话共用。

**Architecture:** 先交付协议数据，不直接改五步 App。标签使用稳定 ID，同行人答案使用 kinds 数组，结构验证和 SOLO 互斥语义由同一 Schema 表达；既有本地 profile v1 与最终 itinerary schema 保持不动。

**Tech Stack:** JSON、JSON Schema 2020-12、Python 3.9+ / jsonschema 4.x、unittest；后续消费者为现有 Java/XML Android。

## Global Constraints

- 本计划只覆盖第一个可独立验收增量；不是整个七轮 App 的完成计划。用户已确认整体设计，不再重复询问功能需求。
- 依据 `docs/superpowers/specs/2026-10-23-profile-planning-session-design.md`；英文数据/界面，中文说明。
- 60 个稳定标签，允许选择 0–60；运动兴趣不代表资格或经验。
- 同行人 Solo/Partner/Family/Friends 多选；Solo 独占。其他三个可组合，确认后才前进。
- 人数包括用户本人；SOLO=1，非 SOLO 未知=null、已知至少 2；不得把标签个数当人数。
- 不改真实后端、不加依赖到 Android、不连接网络、不修改或迁移现有用户数据。
- 用户此前要求在 main 开发；保持 Oct-21-backup 不动，不推送远程。每个任务先检查工作区，提交只包含本任务文件。

## 后续拆分顺序

本计划完成后，剩余三个可独立审查的计划按依赖顺序编写和实施：完整会话 Schema/全部轮次样本及 Mock 服务 → 档案 v2 与首次标签页 → 七轮 UI、持久化、回退与行程生成。不能只完成本计划就宣称用户系统或七轮交互已实现。

## Task 1: 固化 60 个标签目录

**Files:**
- Create: `docs/planning-session/travel-tags.json`
- Create: `docs/planning-session/tests/test_preference_contract.py`
- Read: `docs/superpowers/specs/2026-10-23-profile-planning-session-design.md`

**Interfaces:** 消费设计表格中的 ID/English label；产生 `travel-tags.json` 对象 `{schemaVersion, tags}`，tags 项为 `{id, label}`。UI 的分组映射是显示层元数据，不改变这些 ID。

- [ ] 新建以下完整测试文件。首个测试把文档与机器目录逐项对照，不只检查数量。

```python
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT.parent / 'superpowers/specs/2026-10-23-profile-planning-session-design.md'

class PreferenceContractTest(unittest.TestCase):
    def test_catalog_matches_all_sixty_documented_tags(self):
        table = SPEC.read_text(encoding='utf-8').split('固定目录 v1')[1].split('标签表示初始偏好')[0]
        expected = []
        for line in table.splitlines():
            if not re.match(r'^\| [A-Z_]+ \|', line) or line.startswith('| ID |'):
                continue
            cells = [cell.strip() for cell in line.split('|')]
            expected.extend({'id': cells[i], 'label': cells[i + 1]} for i in (1, 3, 5))
        catalog = json.loads((ROOT / 'travel-tags.json').read_text(encoding='utf-8'))
        self.assertEqual(set(catalog), {'schemaVersion', 'tags'})
        self.assertEqual(catalog['schemaVersion'], '1.0')
        self.assertEqual(catalog['tags'], expected)
        self.assertEqual(len(expected), 60)
        self.assertEqual(len({tag['id'] for tag in expected}), 60)
        self.assertIn({'id': 'DIVING', 'label': 'Diving'}, expected)
        self.assertIn({'id': 'SNORKELING', 'label': 'Snorkeling'}, expected)

if __name__ == '__main__':
    unittest.main()
```

- [ ] 运行 `python3 -m unittest discover -s docs/planning-session/tests -p 'test_preference_contract.py' -v`，确认因 travel-tags.json 不存在而失败；环境缺依赖不算有效红灯。
- [ ] 使用 apply_patch 新增以下完整目录，不重命名旧 ID。

```json
{
  "schemaVersion": "1.0",
  "tags": [
    {"id":"INTERNATIONAL","label":"International"},
    {"id":"DOMESTIC","label":"Domestic"},
    {"id":"WEEKEND_BREAKS","label":"Weekend breaks"},
    {"id":"LONG_HAUL","label":"Long-haul trips"},
    {"id":"ROAD_TRIPS","label":"Road trips"},
    {"id":"RAIL_TRAVEL","label":"Train journeys"},
    {"id":"ISLANDS","label":"Islands"},
    {"id":"BEACHES","label":"Beaches"},
    {"id":"MOUNTAINS","label":"Mountains"},
    {"id":"COUNTRYSIDE","label":"Countryside"},
    {"id":"CITY_BREAKS","label":"City breaks"},
    {"id":"NATURE","label":"Nature"},
    {"id":"SHOPPING","label":"Shopping"},
    {"id":"FOOD","label":"Local food"},
    {"id":"CAFES","label":"Cafés"},
    {"id":"MUSEUMS","label":"Museums"},
    {"id":"ART","label":"Art & galleries"},
    {"id":"HISTORY","label":"History"},
    {"id":"ARCHITECTURE","label":"Architecture"},
    {"id":"CULTURE","label":"Local culture"},
    {"id":"PHOTOGRAPHY","label":"Photography"},
    {"id":"MUSIC","label":"Live music"},
    {"id":"FESTIVALS","label":"Festivals"},
    {"id":"NIGHTLIFE","label":"Nightlife"},
    {"id":"HIKING","label":"Hiking"},
    {"id":"CYCLING","label":"Cycling"},
    {"id":"WATER_ACTIVITIES","label":"Water activities"},
    {"id":"WILDLIFE","label":"Wildlife"},
    {"id":"ADVENTURE","label":"Adventure"},
    {"id":"WELLNESS","label":"Wellness"},
    {"id":"SLOW_TRAVEL","label":"Slow travel"},
    {"id":"PACKED_ITINERARY","label":"See more places"},
    {"id":"HIDDEN_GEMS","label":"Hidden gems"},
    {"id":"LUXURY","label":"Luxury stays"},
    {"id":"BUDGET_FRIENDLY","label":"Budget-friendly"},
    {"id":"ECO_CONSCIOUS","label":"Eco-conscious"},
    {"id":"DIVING","label":"Diving"},
    {"id":"SNORKELING","label":"Snorkeling"},
    {"id":"FREEDIVING","label":"Freediving"},
    {"id":"SURFING","label":"Surfing"},
    {"id":"KITESURFING","label":"Kitesurfing"},
    {"id":"WINDSURFING","label":"Windsurfing"},
    {"id":"KAYAKING","label":"Kayaking"},
    {"id":"CANOEING","label":"Canoeing"},
    {"id":"PADDLEBOARDING","label":"Paddleboarding"},
    {"id":"SAILING","label":"Sailing"},
    {"id":"RAFTING","label":"Rafting"},
    {"id":"SWIMMING","label":"Swimming"},
    {"id":"SKIING","label":"Skiing"},
    {"id":"SNOWBOARDING","label":"Snowboarding"},
    {"id":"ICE_SKATING","label":"Ice skating"},
    {"id":"ROCK_CLIMBING","label":"Rock climbing"},
    {"id":"BOULDERING","label":"Bouldering"},
    {"id":"TRAIL_RUNNING","label":"Trail running"},
    {"id":"RUNNING","label":"Running"},
    {"id":"MOUNTAIN_BIKING","label":"Mountain biking"},
    {"id":"HORSE_RIDING","label":"Horse riding"},
    {"id":"TENNIS","label":"Tennis"},
    {"id":"GOLF","label":"Golf"},
    {"id":"SPORTS_EVENTS","label":"Sports events"}
  ]
}
```

- [ ] 重跑上述命令，预期 1 test、OK；检查 `git diff --check`。
- [ ] 仅提交目录与测试：`git add docs/planning-session/travel-tags.json docs/planning-session/tests/test_preference_contract.py`，`git commit -m "feat: define sixty stable travel preference tags"`。

## Task 2: 同行人多选答案与选择输入 Schema

**Files:**
- Create: `docs/planning-session/companions.schema.json`
- Create: `docs/planning-session/tests/test_companions_contract.py`
- Read: `docs/planning-session/examples/companions.answer.json`
- Read: `docs/planning-session/examples/confirm-companions.request.json`

**Interfaces:** Root 验证 `{value,source}` 答案；`#/$defs/selection` 验证 TurnRequest.input。未来完整会话 Schema 本地导入同样定义，不能复制后让规则分叉。这里只校验子类型，不冒充完整 TurnRequest 校验。

- [ ] 新建以下完整测试文件；执行前确认运行环境能 `import jsonschema`，优先复用现有协议验证环境，不给 Android 加依赖。

```python
import copy
import json
import unittest
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[1]

class CompanionsContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads((ROOT / 'companions.schema.json').read_text(encoding='utf-8'))
        Draft202012Validator.check_schema(cls.schema)
        cls.answer = Draft202012Validator(cls.schema)
        selection_root = copy.deepcopy(cls.schema)
        selection_root['$ref'] = '#/$defs/selection'
        cls.selection = Draft202012Validator(selection_root)

    def test_published_examples(self):
        answer = json.loads((ROOT / 'examples/companions.answer.json').read_text(encoding='utf-8'))
        request = json.loads((ROOT / 'examples/confirm-companions.request.json').read_text(encoding='utf-8'))
        self.answer.validate(answer)
        self.selection.validate(request['input'])
        self.assertIsNone(answer['value']['partySize'])

    def test_solo_and_groups(self):
        for kinds, size, expected in [(['SOLO'],1,True), (['SOLO'],None,False),
                (['SOLO','FAMILY'],3,False), (['PARTNER','FRIENDS'],None,True),
                (['PARTNER','FRIENDS'],4,True), (['FAMILY'],1,False),
                (['FAMILY'],31,False), (['FAMILY','FAMILY'],3,False), ([],None,False)]:
            with self.subTest(kinds=kinds, size=size):
                answer = {'source':'OPTION','value':{'type':'COMPANIONS','kinds':kinds,
                          'partySize':size,'description':'Example travellers'}}
                self.assertEqual(self.answer.is_valid(answer), expected)

    def test_selected_option_ids(self):
        for ids, expected in [(['companions_solo'],True),
                (['companions_partner','companions_family','companions_friends'],True),
                (['companions_solo','companions_friends'],False),
                (['companions_friends','companions_friends'],False),
                (['unknown'],False), ([],False)]:
            with self.subTest(ids=ids):
                self.assertEqual(self.selection.is_valid({'type':'OPTIONS','optionIds':ids}), expected)

    def test_old_scalar_kind_is_not_accepted(self):
        self.assertFalse(self.answer.is_valid({'source':'OPTION','value':{
            'type':'COMPANIONS','kind':'FRIENDS','partySize':3,'description':'Old shape'}}))

if __name__ == '__main__':
    unittest.main()
```

- [ ] 运行 `python3 -m unittest discover -s docs/planning-session/tests -p 'test_companions_contract.py' -v`，确认因目标 Schema 缺失失败。
- [ ] 使用 apply_patch 新建以下完整 Schema。

```json
{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "title":"Odyssey companion multi-selection contract",
  "$ref":"#/$defs/answer",
  "$defs":{
    "answer":{
      "type":"object","additionalProperties":false,
      "required":["source","value"],
      "properties":{
        "source":{"enum":["OPTION","TEXT"]},
        "value":{"oneOf":[{"$ref":"#/$defs/solo"},{"$ref":"#/$defs/group"}]}
      }
    },
    "solo":{
      "type":"object","additionalProperties":false,
      "required":["type","kinds","partySize","description"],
      "properties":{
        "type":{"const":"COMPANIONS"},
        "kinds":{"type":"array","minItems":1,"maxItems":1,"items":{"const":"SOLO"}},
        "partySize":{"type":"integer","const":1},
        "description":{"type":"string","maxLength":2000}
      }
    },
    "group":{
      "type":"object","additionalProperties":false,
      "required":["type","kinds","partySize","description"],
      "properties":{
        "type":{"const":"COMPANIONS"},
        "kinds":{"type":"array","minItems":1,"maxItems":4,"uniqueItems":true,
          "items":{"enum":["PARTNER","FAMILY","FRIENDS","CUSTOM"]}},
        "partySize":{"anyOf":[{"type":"null"},{"type":"integer","minimum":2,"maximum":30}]},
        "description":{"type":"string","maxLength":2000}
      }
    },
    "selection":{
      "type":"object","additionalProperties":false,"required":["type","optionIds"],
      "properties":{
        "type":{"const":"OPTIONS"},
        "optionIds":{"oneOf":[
          {"type":"array","minItems":1,"maxItems":1,"items":{"const":"companions_solo"}},
          {"type":"array","minItems":1,"maxItems":3,"uniqueItems":true,
            "items":{"enum":["companions_partner","companions_family","companions_friends"]}}
        ]}
      }
    }
  }
}
```

- [ ] 执行 `python3 -m unittest discover -s docs/planning-session/tests -p 'test_*contract.py' -v`，预期 Task 1 与 Task 2 共 5 项测试通过；用既有 itinerary 验证命令确认没有改动最终协议。
- [ ] 仅提交本任务 Schema 与测试：`git add docs/planning-session/companions.schema.json docs/planning-session/tests/test_companions_contract.py`，`git commit -m "feat: validate multi-select companions without inferring group size"`。

## Task 3: 交付边界与下一增量

**Files:** Modify `docs/planning-session/README.md`。

**Interfaces:** README 只链接已实际生成且通过测试的文件；不能称剩余会话协议已实现。

- [ ] 在 README 追加以下文本（本计划执行通过后才能追加）：

```markdown
## 已验证的协议子集

- [60 个标签目录](travel-tags.json)：ID、英文名称与设计文档一一对应。
- [同行人 Schema](companions.schema.json)：根节点验证答案；`#/$defs/selection` 验证同行人选项输入。
- `python3 -m unittest discover -s docs/planning-session/tests -p 'test_*contract.py' -v` 验证这些子类型；不代表完整会话或 App UI 已实现。
- 下一增量：完整会话 Schema、八轮样本与 Mock 服务；随后实现档案页面及七轮 UI。继续沿用已确认设计，不重新决定标签或同行人规则。
```

- [ ] 重跑 5 项测试，检查 README 链接文件确实存在。
- [ ] 执行 `git diff --check`、`git status --short`，确保没有 App/真实后端改动与无关文件进入提交。
- [ ] 提交 README：`git add docs/planning-session/README.md`，`git commit -m "docs: hand off validated preference and companion contracts"`。
- [ ] 报告本增量通过的验证与尚未实现的模块，不推送远程。

## 计划自检

- [x] 标签表 36＋24=60，旧 ID 不变，包含 Diving、Snorkeling 等运动。
- [x] 同行人多选、SOLO 互斥、未知人数 null、旧单值字段拒绝均有具体测试。
- [x] 不将档案 60 标签上限误用于问卷单轮选项限制。
- [x] 本计划明确只交付协议子集；完整会话/档案 UI/七轮 UI 保持独立后续增量。
- [x] 每个实现任务都有完整目标数据/代码、失败验证、通过验证和独立提交。
