# 需求试纸（SpecLitmus）

需求试纸帮助开发者和测试人员检查需求中的歧义与边界：提交需求后生成有原文依据的反例问题，用户补充规则后生成带依据的验收用例，最后导出 Markdown。模型输出是供人复核的建议，不替用户决定业务规则。

## 项目逻辑

1. 用户在 Vue 页面粘贴需求；前后端都将按 Java/JavaScript UTF-16 字符数检查 2,000 字符上限。
2. 后端规范化换行、分段并建立原文快照，再调用 Spring AI 适配的文本模型生成最多 5 个审查问题。
3. 后端验证问题字段及原文引用，计算引用位置后保存通过校验的审查会话；前端展示卡片和对应原文。
4. 用户可以回答问题。后端只把用户明确确认的回答作为规则依据，并生成最多 8 条 Given/When/Then 验收用例，同时列出尚未确认的问题。
5. 前端用当前有效结果生成 Markdown；需求与回答草稿保存在浏览器 localStorage，API Key 只由后端从环境变量读取。

此流程来自 `docs/requirements/PRD.md`。本仓库目前只有工程骨架，以上业务流程、接口和模块均为需求设计，尚未实现。

## 技术栈

- 后端：Java 21、Spring Boot 3.5.16、Maven；Spring AI 1.1.7 依赖管理与 Boot 3.5.x 对齐。模型供应商 starter 将在首个 AI 能力阶段确定。
- 前端：Vue 3.5.43、Vite 8.2.2、JavaScript；通过 Vite 开发代理预留 `/api` 到后端的转发。
- v1 不需要数据库：PRD 规划后端进程内保存最近 20 个会话，浏览器保存草稿。

版本均固定在构建文件中。Spring AI 供应商及凭据尚未配置。

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

需要 Java 21、Maven 3.6.3+（可使用 IntelliJ IDEA 自带的 Maven）、Node.js 20.19+ 或 22.12+ 和 npm。

```sh
cd backend
# 若 Maven 已加入 PATH
mvn spring-boot:run

# macOS IntelliJ IDEA 默认 Maven 路径
'/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn' spring-boot:run
```

另开终端：

```sh
cd frontend
npm install
npm run dev
```

前端开发服务器默认运行于 `http://127.0.0.1:5173`，后端端口为 `8080`。当前尚未实现 API 路由或产品功能；Vite `/api` 代理仅为后续联调预留。

## 需求阶段

PRD 将实现拆为 S1 首轮审查、S2 输入边界与错误恢复、S3 引用校验与定位、S4 有界修复、S5 澄清与验收生成、S6 草稿保护与导出。详见 `docs/requirements/IMPLEMENTATION_PLAN.md`、`TEST_PLAYBOOK.md` 和 `MILESTONES.md`。
