# 后端模板交付报告

状态：DONE_WITH_CONCERNS。两个填写模板、中文使用说明与无依赖形状检查脚本已完成；原示例完整校验因当前 Python 缺 jsonschema 未运行成功。

主任务复验补充（2026-10-12）：使用既有临时验证环境，Schema、2 个完整示例和 8 个拒绝反例均通过；模板形状检查也已独立复跑通过。下方保留子任务当时的环境记录。未向项目新增 Python 依赖。

## 文件

- `docs/itinerary-contract/backend/response.template.json`：18 个完整顶层键，常用对象数组的空占位形状。
- `docs/itinerary-contract/backend/variants.template.json`：5 种事件、7 种交通方式、22 种公共定义。
- `docs/itinerary-contract/backend/README.md`：字段与类型、可空规则、枚举、ID、时间、整数费用、候选互斥、职责边界及可复制英文提示词。
- `docs/itinerary-contract/backend/validate_templates.py`：仅标准库，检查语法、键、必填字段、类型、分支与模板覆盖。

## 验证证据

命令：`python3 docs/itinerary-contract/backend/validate_templates.py`

结果（exit 0）：`PASS: 2 JSON templates; 18 top-level keys; 5 events; 7 modes; 22 shared definitions. Scalar null placeholders intentionally exempted.`

命令：`python3 docs/itinerary-contract/validate_examples.py`

结果（exit 1）：`ModuleNotFoundError: No module named 'jsonschema'`。未新增依赖；该失败属于现有运行环境，不能声称两个完整例子已在本轮通过业务校验。

## 风险与边界

模板可解析但刻意不是合法业务响应：不允许为空的标量用 null 表示尚未填写，填写后必须接受原 Schema 和独立业务校验。对象数组单个示意对象不满足所有真实最小数量约束；住宿 AVAILABLE 需实际 3–5 个候选。辅助模板集合的目录键不得出现在成功响应。

仅新增本目录文件及本报告；没有修改原 Schema、现有示例或 Frontend，没有 Git 提交。App 暂不接网络；文档没有声明具体前端功能已实现。形状校验不证明报价、酒店、签证、地理可行性或图片授权。
