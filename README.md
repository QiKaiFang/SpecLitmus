# 需求试纸（SpecLitmus）

需求试纸帮助开发者和测试人员检查需求中的歧义与边界：提交需求后生成有原文依据的反例问题，用户补充规则后生成带依据的验收用例，最后导出 Markdown。模型输出是供人复核的建议，不替用户决定业务规则。

当前实现 S1 首轮审查、S2 状态反馈、S3 引用定位和 S4 有界修复：用户可以提交不超过 2,000 字符的非空需求并查看模型生成的反例问题；页面会区分处理中、空结果和失败状态，并支持手动重试。后端核对每条引用是否为所指段落的连续原文，计算 UTF-16 全局偏移；点击卡片会滚动到原文段落并高亮该引用。无效模型结果最多修复一次，超时和服务不可用不会追加格式修复；最终失败会保留输入并允许重新提交。澄清、验收用例、草稿保存和导出尚未实现。

## 项目逻辑

1. 用户在 Vue 页面粘贴需求；当前显示字符数，2,000 字符上限和拒绝策略属于 S2。
2. S1 后端规范化换行、按空行分段并建立原文快照，再调用 Spring AI 适配的 OpenAI 兼容聊天模型生成最多 5 个审查问题。
3. 后端在调用模型前校验空白输入和 2,000 字符上限，验证问题字段、数量、段落 ID 和逐字引用，创建问题 ID、UTF-16 引用偏移和进程内审查会话；结构或引用校验失败时最多发起一次修复调用，单次业务请求最多两次模型调用。
4. 后续阶段将支持用户回答问题，并只把明确确认的回答作为规则依据，生成最多 8 条 Given/When/Then 验收用例，同时列出待确认问题。
5. 后续阶段将支持 Markdown 导出和浏览器草稿保存。API Key 由后端从环境变量读取。

详细需求和阶段边界见 `docs/requirements/PRD.md`、`IMPLEMENTATION_PLAN.md`、`TEST_PLAYBOOK.md`。

## 技术栈

- 后端：Java 21、Spring Boot 3.5.16、Maven；Spring AI 1.1.8 OpenAI starter，通过 OpenAI 兼容接口调用模型。
- 前端：Vue 3.5.43、Vite 8.2.2、JavaScript；通过 Vite 开发代理预留 `/api` 到后端的转发。
- v1 不需要数据库：审查会话暂存在后端进程内；浏览器草稿保存将在后续阶段实现。

版本均固定在构建文件中。API 密钥不会写入仓库或发送到前端。

## 目录

```text
backend/                 Spring Boot 后端
  src/main/java/...      api、requirement、review、acceptance、ai、session 模块边界
  src/main/resources/    后端运行配置
frontend/                Vue 3 前端
  src/                   Vue 应用入口、根视图和全局样式
docs/requirements/       提供的 PRD、执行清单、测试手册与 Milestone 文档
```

## 本地启动

需要 Java 21、Maven（可使用 IntelliJ IDEA 自带的 Maven）、Node.js 20.19+ 或 22.12+ 和 npm。

本机 acceptance 配置不纳入 Git。首次本地运行时，可将 `backend/src/main/resources/application-acceptance.example.yml` 复制为 `application-acceptance.yml`。本地文件既可通过环境变量引用凭据，也可配置 Spring AI 的 `spring.ai.openai.api-key` 和 `spring.ai.openai.base-url`；直接写入的凭据只保留在被 Git 忽略的本地文件中，不要提交。

如果没有在本机 `application-acceptance.yml` 中配置凭据，先设置真实模型环境变量：

```sh
export OPENAI_API_KEY='你的 API 密钥'
export OPENAI_BASE_URL='https://api.openai.com' # 兼容服务可替换为其 API 根地址
export OPENAI_MODEL='gpt-4o-mini'
```

`OPENAI_BASE_URL`、`OPENAI_MODEL` 和 `AI_REQUEST_TIMEOUT` 都可按服务商调整。Spring AI 的 chat completions 路径也可通过 `spring.ai.openai.chat.completions-path` 或 `OPENAI_CHAT_COMPLETIONS_PATH` 配置。火山引擎 Agent Plan 的兼容路径使用 `/chat/completions`；默认值仍为 `/v1/chat/completions`。本机 Agent Plan 实测需要 120 秒读取超时，下面的真实模式命令采用该值。请勿将密钥提交到 Git。

先构建前端静态文件，供 Spring Boot 在 8080 端口提供页面：

```sh
cd frontend
npm install
npm run build
```

审查自定义需求时，从项目根目录启动真实模型后端（macOS IntelliJ IDEA 默认 Maven 路径）：

```sh
cd backend
APP_AI_MODE=real SPRING_AI_MODEL_CHAT=openai AI_REQUEST_TIMEOUT=120s \
  '/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn' \
  -Dspring-boot.run.profiles=acceptance spring-boot:run
```

打开 `http://127.0.0.1:8080/`。`application.yml` 默认激活 acceptance profile；直接在 IDEA 运行入口类时，模式取决于本机 ignored `application-acceptance.yml` 和 IDEA 运行配置。仓库提供的 example 文件默认使用固定网关；真实调用需要 `speclitmus.ai.mode=real` 和 `spring.ai.model.chat=openai`。凭据可从环境变量读取，也可由本机 acceptance 配置提供。真实模式缺少有效 Key 时，应用仍可启动；审查请求会返回可重试的 `503 AI_CONFIGURATION_MISSING`，且不会自动切换为固定数据。

无需真实密钥即可启动后端固定验收模式：

```sh
cd backend
APP_AI_MODE=stub APP_TEST_SCENARIO=review-valid \
  '/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn' \
  spring-boot:run -Dspring-boot.run.profiles=acceptance
```

固定模式仅支持页面“载入示例”的 R1 原文；提交自定义需求会收到 `FIXTURE_INPUT_UNSUPPORTED`，不会把固定卡片误当成真实模型结果。固定模式仍执行实际后端解析、验证和会话保存，只替换模型响应。前端本地开发时，另开终端运行：

S4 的固定后端场景包括 `repair-ok` 和 `repair-fail`：前者第一次返回不可解析结果、第二次返回合法卡片；后者两次都返回不可解析结果。两种场景均按每次业务请求重新计数，分别预期 200/502，且模型调用数均不超过 2 次。`timeout` 和 `unavailable` 场景各只调用一次，不进入格式修复。

```sh
cd frontend
npm run dev
```

Vite 页面位于 `http://127.0.0.1:5173/`，`/api` 请求代理到后端 `8080`。使用 `?fixture=review-valid` 可单独检查固定前端样例；页面会显著标记固定测试数据，并读取静态 JSON，不发起 `/api` 请求。执行静态前端验收时，按 `docs/requirements/TEST_PLAYBOOK.md` 使用 `jwebserver`。

如果使用仓库提供的 `backend/mvnw`，它会按 `.mvn/wrapper/maven-wrapper.properties` 选择 Maven 版本；也可以直接使用 IDE 自带 Maven。

## 需求阶段

PRD 将实现拆为 S1 首轮审查、S2 输入边界与错误恢复、S3 引用校验与定位、S4 有界修复、S5 澄清与验收生成、S6 草稿保护与导出。详见 `docs/requirements/IMPLEMENTATION_PLAN.md`、`TEST_PLAYBOOK.md` 和 `MILESTONES.md`。
