# 需求试纸：后端、前端与阶段联调测试手册

版本：v1.1 · 日期：2026-09-30  
关联：[PRD](PRD.md) · [执行清单](IMPLEMENTATION_PLAN.md) · [Milestone](MILESTONES.md)

> 本文定义分层验收流程和阶段测试约定。当前执行状态见 `IMPLEMENTATION_PLAN.md`，已执行结果以 `docs/evidence/` 中的请求、响应和记录为准。不能把命令未报错、固定数据展示或本文件中的预期结果当成真实模型验收通过。

## 1. 三种局部环境与一种真实验证

| 模式 | 启动什么 | 替换什么 | 验证什么 |
|---|---|---|---|
| 后端局部 | Spring Boot + 固定模型 | 仅 AiGateway 的外部模型响应 | HTTP、校验、会话和业务规则 |
| 前端局部 | 静态服务器 + 固定 API | ApiClient，不调用后端 | 页面、事件、加载、错误和状态 |
| 前后端联调 | Spring Boot 提供页面和接口 + 固定模型 | 仅模型响应 | 请求字段、响应渲染、完整用户操作 |
| 真实模型验证 | 正常 Spring Boot 应用 + 模型配置 | 无替换 | 实际接入、语义效果及完整链路 |

后端测试不能直接伪造 Controller 最终响应，否则会跳过需要验证的业务代码。前端固定响应只验证展示和交互，不能证明后端规则正确。

### 1.1 后端局部启动

S1 中实现 acceptance profile：以条件配置启用 StubAiGateway，关闭真实供应商初始化。以下为项目约定变量，需要代码主动绑定。

```sh
APP_AI_MODE=stub APP_TEST_SCENARIO=review-valid ./mvnw spring-boot:run -Dspring-boot.run.profiles=acceptance
```

地址为 `http://127.0.0.1:8080`。切换场景时停止进程，修改 APP_TEST_SCENARIO 再启动。重启会使旧 reviewId 失效，应重新审查。

固定模型场景每次业务请求重新从第一个响应开始计数；计数可通过 Spy 单测或仅测试模式的日志核对。测试日志记录操作标识、场景、调用次数，不输出密钥。

### 1.2 前端单独启动

前端将 ApiClient 分为正常 HTTP 实现和 FixtureApiClient。后者读取 `static/test-fixtures/<场景>.json`，有可控延迟及成功/错误响应；通过本地页面参数 `fixture` 选择。显著显示“固定测试数据”，不能静默当作真实结果。

使用 JDK 21 自带的静态服务器：

```sh
jwebserver -b 127.0.0.1 -p 5173 -d "$PWD/src/main/resources/static"
```

打开 `http://127.0.0.1:5173/?fixture=review-valid`。此时后端可以完全关闭。浏览器 Network 只应出现页面和静态 JSON 请求，没有 POST /api/ 请求。

jwebserver 仅提供静态文件；它不承载后端 POST 接口。该命令依据 [JDK 21 官方文档](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jwebserver.html)。

### 1.3 联调与真实模型启动

联调：使用 1.1 的后端，在 `http://127.0.0.1:8080/` 打开页面，URL 不带 fixture 参数。

真实模型：按 README 配好环境变量，停止固定模式后，从 `backend/` 运行 acceptance profile：

```sh
APP_AI_MODE=real SPRING_AI_MODEL_CHAT=openai ./mvnw -Dspring-boot.run.profiles=acceptance spring-boot:run
```

页面不应有固定数据标记。结合后端模式、供应商调用结果、模型标识判断是否真实调用；不能只看页面文案。

## 2. 统一样例与响应契约

### 2.1 输入样例

**R1：正常双段需求。**

```text
用户每天可以领取一次优惠券。

新用户可以额外领取一张。
```

- P001 为第一段，UTF-16 全局位置 [0,14)。
- P002 为第二段，位置 [16,28)。
- “每天”位置 [2,4)；“新用户”位置 [16,19)。索引均左闭右开。
- R1 固定模型返回两张问题：每天的计算口径、新用户的定义。真实模型可有不同结果。

| 样例 | 内容或生成方式 | 用途 |
|---|---|---|
| R2 | 仅空格、制表符和换行 | 空白输入 |
| R3 | “测”重复 2,000 次 | 合法长度边界；搭配 review-empty |
| R4 | “测”重复 2,001 次 | 非法长度；调用模型前拒绝 |
| R5 | 将 R1 的换行改为 CRLF | 规范化 |
| R6 | 第一段为“😀用户每天可以领取一次优惠券。” | UTF-16 偏移；“每天”应为 [4,6) |
| R7 | “用户每天领取一次。系统每天重置。” | 重复引用；“每天”取第一次 |
| A1 | 按北京时间自然日重置。 | 第一个问题的确认回答 |
| A2 | 注册当天属于新用户，额外奖励每个账号仅一次。 | 第二个问题的确认回答 |

R3/R4 可在 Java 测试中使用 `"测".repeat(2000/2001)`，前端用相同长度的字符串。表情按两个 UTF-16 单元计数，与 PRD 一致。

### 2.2 接口字段固定下来再分别开发

审查接口最终契约示例。以下 ID、hash 和模型标识都是固定样例，不可用于真实会话：

```json
{
  "reviewId": "fixture-review-001",
  "requirementText": "用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。",
  "requirementHash": "fixture-only",
  "paragraphs": [
    {"id":"P001","text":"用户每天可以领取一次优惠券。","start":0,"end":14},
    {"id":"P002","text":"新用户可以额外领取一张。","start":16,"end":28}
  ],
  "questions": [
    {
      "questionId":"Q001","type":"AMBIGUITY","paragraphId":"P001",
      "quote":"每天","quoteStart":2,"quoteEnd":4,
      "scenario":"23:59 领取后，次日 00:01 再次领取。",
      "question":"每天按自然日还是间隔 24 小时计算？",
      "possibleInterpretations":["自然日","滚动 24 小时"]
    },
    {
      "questionId":"Q002","type":"AMBIGUITY","paragraphId":"P002",
      "quote":"新用户","quoteStart":16,"quoteEnd":19,
      "scenario":"注册三天但从未领券的用户首次领取。",
      "question":"新用户如何定义？",
      "possibleInterpretations":[]
    }
  ],
  "metadata":{"model":"fixture","promptVersion":"review-v1"}
}
```

S1/S2 尚未实现位置核验时，quoteStart/quoteEnd 可以为空，前端只展示文本；S3 起由后端计算并校验位置。固定数据可以包含完整字段，但不能用它证明提前完成了高亮功能。

后端模型替身只返回模型负责的 questions 内容，不返回 reviewId、questionId、hash、位置和 metadata；这些由真实业务代码补充。前端样例则使用完整 API 响应，二者不要混用。

验收响应固定字段为 `cases`、`pendingQuestionIds`、`warnings`、`metadata`。case 包含 caseId/title/given/when/then/basis；basis 元素为 sourceType/sourceId/quote。ORIGINAL 的 sourceId 指段落，ANSWER 的 sourceId 指当前 questionId。

前端 `acceptance-partial` 响应示例（对应 R1、固定 Q001/Q002，只填写 A1）：

```json
{
  "cases": [{
    "caseId": "C001",
    "title": "跨北京时间自然日领取",
    "given": "用户已在北京时间 10 月 1 日 23:59 领取优惠券。",
    "when": "用户于北京时间 10 月 2 日 00:01 再次领取。",
    "then": "可按新自然日领取优惠券。",
    "basis": [
      {"sourceType":"ORIGINAL","sourceId":"P001","quote":"用户每天可以领取一次优惠券。"},
      {"sourceType":"ANSWER","sourceId":"Q001","quote":"按北京时间自然日重置。"}
    ]
  }],
  "pendingQuestionIds": ["Q002"],
  "warnings": [],
  "metadata": {"model":"fixture","promptVersion":"acceptance-v1"}
}
```

这里的时间点是构造的测试数据，规则来自原文与 A1。后端替身用本次实际 questionId 构造模型依据，pendingQuestionIds 仍由真实服务计算，不能直接套用固定 Q001/Q002。

错误响应示例：

```json
{"code":"AI_OUTPUT_INVALID","message":"本次结果未通过校验，请重试。","retryable":true}
```

### 2.3 逐阶段建立的固定场景

| 后端场景 | 行为 | 首次需要 |
|---|---|---|
| review-valid | 为 R1 返回两个合法问题；ID 由真实服务分配 | S1 |
| review-empty | 返回 questions=[]，适用于任何合法长度文本 | S2 |
| unavailable / timeout | 抛出可控的供应商不可用/超时异常 | S2 |
| bad-paragraph / bad-quote | 每次都返回不存在段落/不在原文中的引用 | S3 |
| repair-ok | 首次非法 JSON，第二次合法问题 | S4 |
| repair-fail | 两次都是非法 JSON | S4 |
| workflow-partial | 审查返回两问；验收返回基于 A1 的用例，另一个问题未回答 | S5 |
| workflow-all | 两问均回答后返回对应依据的用例 | S5 |
| workflow | 按本次回答数选择预置 partial/all 模板；供同一会话反复生成测试 | S5 |
| workflow-empty | 审查返回 0 问；验收返回合法结果，允许 0 用例和依据不足说明 | S5 |
| acceptance-bad-basis | 审查合法；验收持续引用不存在的回答 | S5 |

R6/R7 的合法模型返回需引用对应输入；不要拿 R1 固定引用测试任意文本而误判解析器。修复还要用“非法引用→合法引用”序列做服务测试，证明不只修复 JSON 格式。

前端场景：review-valid、review-empty、review-delayed、review-error、error-then-success、evidence-special、acceptance-partial、acceptance-all、acceptance-empty、session-expired、report-special。按阶段实现，不要求 S1 就提供全部场景；review-valid 的位置字段在 S3 用于高亮。

前端场景文件是 API 响应包，可按 `review` / `acceptance` 两个操作分别保存 `{status,delayMs,body}` 的响应序列。单次页面会话内按顺序取值，序列结束重复最后一项；刷新重置。只负责返回固定样例，不重复实现后端业务规则。错误 JSON 等模型故障在前端表现为标准 HTTP 错误体。

## 3. 后端可复制的请求流程

在新项目根目录运行，先启动对应阶段已实现的后端。以下操作不会自动测试尚未实现的功能。

### 3.1 发起审查

```sh
mkdir -p work/qa
cat > work/qa/review-request.json <<'JSON'
{"requirementText":"用户每天可以领取一次优惠券。\n\n新用户可以额外领取一张。"}
JSON
curl --silent --show-error \
  --dump-header work/qa/review.headers \
  --output work/qa/review-response.json \
  -H 'Content-Type: application/json' \
  --data-binary @work/qa/review-request.json \
  http://127.0.0.1:8080/api/reviews
```

查看 headers 中状态码，以及 JSON 字段。此处不使用 `curl --fail`，便于保留 400/502 等错误响应。每个案例使用独立证据文件名，避免覆盖上个案例。

### 3.2 提交澄清（S5 起）

从刚才的响应复制 reviewId 和第一张卡片的 questionId。编辑请求文件，把示例占位值换成实际 ID：

```json
{"answers":[{"questionId":"替换成第一张卡片的实际ID","text":"按北京时间自然日重置。"}]}
```

保存为 work/qa/acceptance-request.json，再执行：

```sh
review_id='替换成刚才响应的实际reviewId'
curl --silent --show-error \
  --dump-header work/qa/acceptance.headers \
  --output work/qa/acceptance-response.json \
  -H 'Content-Type: application/json' \
  --data-binary @work/qa/acceptance-request.json \
  "http://127.0.0.1:8080/api/reviews/$review_id/acceptance"
```

不能使用 fixture-review-001 调用真实后端。切换场景并重启后必须重新执行审查。未知 ID 案例才故意使用不存在的值。

### 3.3 自动测试入口

建议将服务规则写成 JUnit 测试，不仅依赖人工调用。以下测试类是待创建的约定名称；若改名，同时更新文档。类不存在或实际执行 0 条测试，均不能记作通过。

| 阶段 | 建议命令 |
|---|---|
| S1 | `./mvnw -Dtest=ReviewApiTest test` |
| S2 | `./mvnw -Dtest=ReviewInputTest,AiFailureTest test` |
| S3 | `./mvnw -Dtest=RequirementParserTest,EvidenceValidatorTest test` |
| S4 | `./mvnw -Dtest=ReviewRepairTest test` |
| S5 | `./mvnw -Dtest=AcceptanceApiTest,AcceptanceEvidenceTest test` |
| S6 | `./mvnw -Dtest=ReviewSessionStoreTest test`，随后运行 `./mvnw test` |

## S1

前置：正常审查接口、输入页、review-valid 后端替身和前端固定数据已实现。

| ID | 操作流程 | 预期及证据 |
|---|---|---|
| S1-B01 | 关闭前端；启动后端 review-valid；按 3.1 提交 R1 | 200；两张完整卡片；reviewId 非空；ID 唯一；保存请求/响应 |
| S1-B02 | 停固定模式，按 README 启动真实模型；再次提交 R1 | 实际模型调用成功，结构可用；人工看场景/问题；记录版本及真实结果，不断言固定两张 |
| S1-F01 | 关闭后端；静态服务器打开 review-valid；载入 R1、点击审查 | 两张卡片、无 POST /api/、固定数据标记可见；截图与 Network 记录 |
| S1-I01 | 启真实模型应用；页面不带 fixture；输入 R1、提交；对照响应和页面 | 页面显示本次真实结果；输入/响应一致；人工检查没有残留固定数据 |

阶段出口：四项都通过。后端 B 或前端 F 单独通过时，可先提交对应代码，不提前声明整阶段完成。

## S2

前置：S1 通过；空结果/错误协议、页面状态和相应场景已实现。

| ID | 操作流程 | 预期及证据 |
|---|---|---|
| S2-B01 | review-empty 模式依次请求 R2、R4、R3；另发缺 requirementText 的请求 | 前三次分别 400/400/200；缺字段 400；非法请求的模型计数为 0 |
| S2-B02 | review-empty 请求 R1 | 200 且 questions=[]；有效 reviewId；不是“模型失败” |
| S2-B03 | 分别切 unavailable 和 timeout，提交 R1；再在适配器测试中把超时配置缩短为 100ms，让本地模拟供应商延迟 500ms 返回 | 分别得到 503 AI_UNAVAILABLE / 504 AI_TIMEOUT；各 1 次调用。延迟测试证明超时配置确实生效，在宽松测试时间窗内结束且不等待完整响应；仅主动抛超时异常不能证明配置生效 |
| S2-B04 | 真实模式移除模型配置后启动并请求；再切无 Key 的固定模式 | 真实配置缺失可读且不泄漏密钥；固定模式正常启动。若依赖在启动期拒绝缺配置，需调整为 PRD 约定的请求错误行为 |
| S2-F01 | review-delayed 延迟 800ms 以上；连续点击提交；再打开 review-empty | 延迟时输入冻结、只有 1 次 ApiClient 调用；空结果显示“本次未发现需要确认的问题” |
| S2-F02 | 在页面输入 R2/R4，确认拦截；切 review-error 提交 R1，再修改输入 | 无效输入不调用 ApiClient；失败后输入保留且可编辑、重试 |
| S2-F03 | 固定卡片 scenario 含字面文本 `<b>示例</b>` | 展示字面文本，不生成 b 元素、不执行 HTML |
| S2-I01 | 真前后端分别用 empty/unavailable；然后停止后端提交，再重启 review-valid 重试 | 三种状态分别可识别；无输入丢失；恢复后能重新取得结果 |

回归 S1 正常链路。0 卡片的验收生成在 S5 测，不因尚未实现而判 S2 失败。

## S3

前置：S2 通过；引用校验、位置输出和高亮已实现。

| ID | 操作流程 | 预期及证据 |
|---|---|---|
| S3-B01 | Parser/Validator 测试 R1/R5；对相同文本重复解析 | 规范化后的正文、段落 ID、hash 一致；R1 位置符合 2.1；substring 等于引用 |
| S3-B02 | 用 R6/R7 及匹配引用测试定位 | 表情不造成偏移；重复引用取第一次；保存测试结果 |
| S3-B03 | bad-paragraph 和 bad-quote 分别请求 R1；另测 6 张卡片、缺字段、空白引用 | 502 AI_OUTPUT_INVALID；没有有效 reviewId/卡片。S3 为 1 次调用；S4 后最多 2 次且最终仍失败 |
| S3-F01 | review-valid 固定数据含 R1 的两张卡片与位置；依次点 1、2、1 | 分别定位 P001/P002/P001；高亮 quote 对应部分，旧高亮消失 |
| S3-F02 | evidence-special 使用 R6/R7 和 HTML 形状文本 | 位置正确，文本不作为 HTML 执行；截图 |
| S3-I01 | 真前后端用合法引用提交并点击两张卡片；再切持续错误引用场景重试 | 高亮基于真实后端返回的位置；最终错误不能继续显示为本次成功；旧结果有明确过期/非当前标记或被清理 |

回归 S1 的正常展示与 S2 的空结果。引用存在性通过不代表语义判断必然正确。

## S4

前置：S3 通过；修复序列、调用计数和页面恢复已实现。

| ID | 操作流程 | 预期及证据 |
|---|---|---|
| S4-B01 | repair-ok 请求 R1；另用“错误引用→正确引用”序列跑服务测试 | 最终 200，恰好 2 次模型调用；只保存最终合法结果 |
| S4-B02 | repair-fail 请求 R1，结束后再发第二个业务请求 | 每个请求均 502 且各 2 次；序列重新计数；无第 3 次调用 |
| S4-B03 | review-valid、timeout、unavailable 分别测试 | 各 1 次；200/504/503；超时和不可用不误触发格式修复 |
| S4-B04 | Spy/计数验证 SDK 与服务的总调用数；运行 ReviewRepairTest | 正常 1、修复最多 2；不只验证外层 for 循环次数 |
| S4-F01 | 前端延迟成功样例，处理中反复点击；检查 ApiClient 调用数 | 一个浏览器业务请求，随后展示最终结果；无需模拟模型内部调用 |
| S4-F02 | error-then-success：首次失败，手动再点击成功 | 原文保留，按钮恢复；成功后旧错误清除，不重复叠加卡片 |
| S4-I01 | 真前后端先用 repair-ok，再用 repair-fail | 浏览器各 1 个业务请求，后端各 2 次模型调用；最终状态分别成功/失败；保存两侧证据 |

回归持续 bad-quote：最终仍须 502。不要把“新增了修复”误判为“旧测试必须仍只调用 1 次”。

## S5

前置：S4 通过；验收接口、回答表单、用例展示与输入版本状态已实现。

| ID | 操作流程 | 预期及证据 |
|---|---|---|
| S5-B01 | workflow-partial 新建 R1 审查，复制实际 ID；只提交第一问 A1；再用适配全部回答的场景重新建立审查并提交 A1/A2 | 部分回答时 200、有用例与依据，第二问保留待确认；全部回答后 pending 为空。切场景重启后不能复用旧 reviewId |
| S5-B02 | 对当前会话分别提交未知 questionId、重复 ID、501 字符；再用不存在 reviewId；另提交仅空白的答案 | 非法问题/超长回答 400，失效会话 410，均不调用验收模型；空白答案按未回答处理，问题仍待确认 |
| S5-B03 | acceptance-bad-basis 新建审查后提交回答 | 修复后仍非法则 502，验收模型最多调用 2 次；不发布无依据用例 |
| S5-B04 | workflow-empty 建立 0 卡片审查，提交 answers=[]；另测依据不足返回 0 用例并附说明 | 都能执行验收流程；结构和依据合法；不为凑数编造规则 |
| S5-B05 | 在当前 reviewId 下依次提交部分/全部回答，核对 basis、pending | basis 只能指原文或本次已确认回答；pending 由后端计算，不能被模型遗漏。语义真实性由真实评测补充 |
| S5-F01 | acceptance-partial 模式填 A1，点击生成；查看 FixtureApiClient 捕获的入参 | 只提交第一问答案；显示 Given/When/Then、依据及第二问待确认 |
| S5-F02 | 生成后修改 A1，再修改原文 | 修改回答使旧用例过期；修改原文必须重审，不能携带旧卡片继续生成 |
| S5-F03 | 分别加载 acceptance-empty（审查 0 卡片、验收 0 用例附说明）和 session-expired 场景 | 空卡片仍可生成；会话过期提示重审并保留文本；依据不足说明可见 |
| S5-I01 | 真前后端启用 workflow：R1→两问→A1→生成→补 A2→再生成；随后重启后端提交旧会话 | 第一次有待确认项，第二次更新；过期请求 410；没有旧结果被当成当前结果 |
| S5-I02 | 使用真实模型对同样流程验证；依据实际卡片填写，不假设固定问数/ID | 用例符合用户确认；没有回答的规则不被写成确定预期；保留实际输出和人工结论 |

固定工作流的响应可根据本次传入的真实问题 ID 套入预置样例，但不能绕过 EvidenceValidator 或 pending 计算。前端固定场景的输入须与样例说明一致，不用它检验任意输入语义。

## S6

前置：S5 通过；草稿存储、结果有效性和 Markdown 导出已实现。

| ID | 操作流程 | 预期及证据 |
|---|---|---|
| S6-B01 | 用接口完成审查及验收，逐项对照报告需要的数据 | 原文、问题、回答、用例、依据、版本可取得；没有新增导出接口也可完成 |
| S6-B02 | 测试模式连续创建 21 份审查；访问第一份和最新一份；再重启访问最新一份 | 第一份 410；进程存活时最新可用；重启后旧会话 410。使用测试计数避免真实模型费用 |
| S6-F01 | 填原文/回答、刷新、查看草稿；清除后再刷新 | 草稿能恢复再清除；恢复状态不冒充有效后端会话，不自动绑定旧回答 |
| S6-F02 | 生成有效报告，修改回答后点击导出；重新生成后再导出；再修改原文 | 旧结果不可导出；重新生成可导出；改原文必须重新审查 |
| S6-F03 | report-special 含中文、多行、竖线、反引号；下载并用文本/Markdown 查看 | 章节、引用、回答、待确认项、用例、版本完整可读；与当前数据一致 |
| S6-F04 | 真实/固定模式下查看浏览器存储和下载内容；失败后重试再导出 | 存储不含 Key；不包含内部错误正文；下载的是最后有效版本 |
| S6-I01 | 真前后端+固定模型完整执行 R1→审查→部分回答→生成→导出→修改→过期→再生成→导出→刷新恢复 | 每步状态正确，两个报告分别对应两次确认；输入保存而旧会话不冒充有效 |
| S6-I02 | 正常真实模型运行 PRD E01—E08；E08 额外做全部/部分回答，完成一次导出 | 记录实际语义判断、误报和限制；不把模拟数据写成评测。发现必须修复问题先修复再复测 |
| S6-I03 | 从干净目录取得仓库，按 README 配置、测试、启动和完整操作 | ./mvnw test 实际执行且通过；真实演示可复现；报告下载可用；记录 HEAD 与环境 |

本阶段 B 检查没有发现缺失就无需改后端或强行创建 Commit。最终真实评测不是绝对准确率证明。

## 4. 记录、回归和提交

### 4.1 每条用例记录

```text
用例 ID：
阶段/检查点：
代码 Commit：
模式与场景：
前置状态及输入：
执行步骤/命令：
实际状态码、响应或页面表现：
模型/Prompt 版本（真实调用时）：
结论：未执行 / 通过 / 未通过
证据文件：
修复及复测：
```

全部记录初始均为未执行。固定模式保存程序行为证据，真实模式另保存语义观察。API Key、真实客户需求和未脱敏调用日志不进入提交材料。

### 4.2 回归范围

| 变更 | 应回归 |
|---|---|
| 只改卡片样式 | 对应前端显示、点击、窄屏布局 |
| 改原文解析/公共依据校验 | S3 用例；S5 的原文及回答依据校验 |
| 改修复和超时 | S4；S5 验收调用的相同预算 |
| 改 Prompt | 受影响真实语义样例及结构/引用约束 |
| 改状态或草稿 | S5-F02 和 S6 的刷新、过期、导出 |
| 交付前 | 全部自动测试、六阶段主流程、E01—E08 |

### 4.3 提交门槛

- 后端局部用例通过，可提交后端与测试；前端固定用例通过，可提交前端与样例。
- 联调失败时阶段仍是未通过；修复并复测后才登记阶段完成。
- 阶段已有代码通过，允许先提交，不需要等待整个项目完成。
- 有实际修复才写 fix；无代码变化只记录验收，不凑提交数。
- Milestone 完成需要所属阶段的 B/F/I 都有证据，并且新增 AI 能力有真实验证。
