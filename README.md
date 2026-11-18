# AI 智能客服系统

> 基于 RuoYi-Cloud 微服务 + Spring AI + 智谱 GLM 的书店智能客服。
> 覆盖知识库 FAQ、图书正文问答、库存/价格实时查询、订单与发货状态、图书推荐、用户画像与长期记忆。

<p>
  <img alt="JDK" src="https://img.shields.io/badge/JDK-17-blue.svg">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg">
  <img alt="Spring AI" src="https://img.shields.io/badge/Spring%20AI-2.0.2-green.svg">
  <img alt="MySQL" src="https://img.shields.io/badge/MySQL-8.0.46-orange.svg">
  <img alt="Redis" src="https://img.shields.io/badge/Redis%20Stack-RediSearch-red.svg">
  <img alt="Elasticsearch" src="https://img.shields.io/badge/Elasticsearch-9.2-yellow.svg">
</p>

---

## 一、项目简介

书店客服需要同时回答三类问题：

| 类型 | 例子 | 数据来源 |
|---|---|---|
| 业务查询 | 这本书多少钱？有货吗？我的订单到哪了？ | MySQL 实时数据 |
| FAQ | 多久发货？怎么退款？ | 知识库标准答案 |
| 开放问答 | 这本书讲了什么？某个情节在哪一章？ | 书籍正文 RAG + 大模型 |

纯大模型会**编造库存、价格和书名**，纯 FAQ 又答不了开放问题。本项目的核心设计是 **确定性优先**：

> **能用数据库算出来的答案，绝不让模型猜；模型只负责它擅长的措辞和开放问答。**

常见问题（FAQ 直达 ~1s、热门缓存 ~10ms）不走大模型；只有开放问答才走「向量检索 → 知识库直达判定 → 大模型兜底」，且回答要过书名/价格真实性校验。

---

## 二、核心特性

- **9 段式问答编排**：订单号直达 → 发货查询 → 在售查询 → 热门缓存 → 意图识别 → 图书推荐 → 向量检索 → 知识库直达 → 大模型兜底，按「实时性 / 确定性 / 隐私敏感度」排序。
- **发货查询联动真实订单**：问句点名书名 + 已登录 + 本人有该书订单 → 直接回答「已发货 / 待发货 + 订单号 + 下单时间」；查不到再回退通用时效 FAQ。
- **幻觉四层防线**：结构化查询替代生成 → Prompt 白名单约束 → 回答后置校验（书名/价格越界即失败）→ 确定性文案兜底。推荐场景虚构书名/价格 **0 条**。
- **三套独立向量索引**：知识库 / 图书正文 / 用户记忆分索引存储，`filterExpression` 把 `userId`、`bookId` 下推到 Redis，记忆召回 topK 从 200 降到 3，杜绝跨用户串线与多书片段挤占。
- **ES 图书检索**：bigram 中英混排分词 + 加权打分（书名 20 > 短语 8 > 分词 5 > 分类 2 > 简介 1）；ES 只做索引，价格/库存回落 MySQL 取实时值；**ES 宕机自动降级** MySQL FULLTEXT/LIKE。
- **双写一致性**：索引同步失败进补偿队列 + 定时对账 + 分布式锁保证多实例单执行。
- **用户画像与长期记忆**：异步抽取结构化记忆（preference/fact/concern）落库并向量化，问答时注入 Prompt；画像只调语气与推荐倾向，不代表经营范围。
- **安全与隐私**：身份以登录态为准（fail-closed，忽略请求体自报 userId）、订单/画像/记录归属校验、收货人脱敏、全局热门池只收通用标准答案、单用户限流。

---

## 三、技术栈

| 层次 | 技术 | 用途 |
|---|---|---|
| 语言/框架 | JDK 17、Spring Boot 4.0.6、Spring Cloud 2025.1.1 | 微服务基座 |
| 注册/配置 | Nacos 3.2.3（Spring Cloud Alibaba 2025.1.0） | 服务发现 + 配置中心（含大模型密钥） |
| AI 编排 | Spring AI 2.0.2（`ChatClient` + `VectorStore`） | 对话、Embedding、向量检索、Prompt 模板 |
| 大模型 | 智谱 `glm-4-flash` + `embedding-3`（256 维） | 答案生成、语义向量（OpenAI 兼容协议） |
| 向量库 | Redis Stack（RediSearch） | 知识库 / 正文 / 记忆三索引 |
| 关系库 | MySQL 8.0.46 + MyBatis | 图书/订单/知识库/对话/记忆/画像 |
| 全文检索 | Elasticsearch 9.2（官方 `elasticsearch-java`） | 图书检索索引 |
| 降级检索 | MySQL FULLTEXT（ngram）+ LIKE | ES 不可用时的兜底 |
| 文档解析 | Apache PDFBox | PDF 正文抽取（扫描版降级为仅预览） |
| 缓存/会话 | Redis（Hash + ZSet + List） | 热门问题池、多轮会话、配置缓存、限流 |
| 前端 | Vue 2.6 + Element UI 2.15 + Vuex + Vue Router 3 | 管理后台、AI 客服页面、在线阅读 |
| 文档/部署 | Docker（Redis Stack / ES）、Nginx | 本地编排与反向代理 |

---

## 四、系统架构

```
┌─────────────┐   POST /api/v1/chat/question
│ 前端 / 网关  │ ───────────────────────────────→  AiController
└─────────────┘                                         │
                       ┌────────────────────────────────▼────────────────────────────────┐
                       │           AiServiceImpl.handleUserQuestion（9 段主链路）          │
                       │  确定性分支优先 → 缓存 → 意图 → 检索 → 直达 → 大模型 → 后置校验    │
                       └───────┬───────────────┬───────────────┬──────────────┬──────────┘
                               │               │               │              │
                 ┌─────────────▼──┐   ┌────────▼───────┐  ┌────▼─────────┐  ┌─▼──────────────┐
                 │ MySQL（事实源） │   │ Redis Stack    │  │ Elasticsearch │  │ 智谱 GLM        │
                 │ 图书/订单/知识库│   │ 3 向量索引 +   │  │ 图书检索索引  │  │ glm-4-flash +   │
                 │ 对话/记忆/画像  │   │ 热门池/会话/限流│  │（只索引不存真值）│  │ embedding-3     │
                 └────────────────┘   └────────────────┘  └──────────────┘  └────────────────┘
```

**依赖降级**：Redis 故障 → 跳过缓存继续走正常链路；向量检索失败 → 仅用知识库问答；ES 超时 → 降级 MySQL 全文/LIKE。三者都不会让接口直接报「服务异常」。

---

## 五、问答主链路

| 顺序 | 分支 | 触发条件 | 数据来源 | 是否调模型 |
|---|---|---|---|---|
| 0 | 参数校验 / 身份绑定 / 限流 / 活跃打点 | 每次请求 | DTO + 登录态 + Redis | 否 |
| 1.1 | 订单号直达 | 问句含 `BO...` 订单号 | `biz_order`（归属校验 + 脱敏） | 否 |
| 1.15 | 发货/物流查询 | 含「发货/物流/快递/多久发」 | 本人订单状态 → 否则知识库 FAQ | 否 |
| 1.2 | 图书在售查询 | 卖吗/多少钱/有货吗/裸书名/分类 | `biz_book`（ES 或 MySQL 降级） | 否 |
| 2 | 热门问题缓存 | 归一化问题命中 | Redis 热门池 | 否 |
| 3 | 意图识别 | 全部 | `ai_intent`（Redis 缓存 5min） | 否 |
| 3.1 | 图书推荐 | 推荐/书单/预算/偏好 | `biz_book` 结构化检索 + Prompt 措辞 | 仅措辞 |
| 4 | 向量检索 | 未命中以上分支 | `aa_knowledge_index`（topK=3，阈值 0.5） | 否 |
| 5 | 知识库直达 | 归一化文本匹配 / 相似度 ≥ 0.92 | 知识库标准答案 | 否 |
| 6 | 大模型兜底 | 以上都未命中 | 知识库 + 书籍正文 + 画像 + 最近 6 轮 | **是** |
| 7-9 | 落库 / 异步记忆 / 收尾 | 每次回答后 | `ai_chat_record`、`ai_user_memory` | 异步 LLM |

> 详细说明见 [`docs/AI智能客服-完整流程梳理-20261118.md`](docs/AI智能客服-完整流程梳理-20261118.md)

---

## 六、目录结构（AI 相关）

```
ruoyi-modules/ruoyi-system/src/main/java/com/ruoyi/system/
├── controller/
│   ├── AiController.java                 // 对话 / 画像 / 记录 / 热门问题 / 清历史
│   ├── AiKnowledgeController.java        // 知识库 CRUD（自动同步向量）
│   ├── AiBookContentController.java      // 正文上传 / 大纲 / 在线阅读 / 原件
│   ├── BookSearchController.java         // ES 状态 / 手动重建
│   ├── BizBookController.java            // 图书管理
│   └── BizOrderController.java           // 订单管理
├── service/impl/
│   ├── AiServiceImpl.java                // 主链路（约 1740 行，按分区注释）
│   ├── AiMemoryServiceImpl.java          // 长期记忆 + 用户画像
│   ├── AiBookContentServiceImpl.java     // 正文解析 / 分片 / 向量化
│   ├── AiKnowledgeServiceImpl.java       // 知识库 + 向量同步
│   └── BookSearchServiceImpl.java        // ES 检索 / 同步 / 对账补偿
├── service/support/
│   ├── AiChatStore.java                  // 热门池 + 多轮会话（Redis 旁路）
│   └── AiTextUtils.java                  // 归一化 / 订单号提取等纯函数
├── search/EsBookSearchClient.java        // ES 官方客户端封装
├── config/
│   ├── RedisVectorConfig.java            // 三个 VectorStore Bean
│   ├── AiAsyncConfig.java                // 有界线程池
│   └── TestSecurityBypassConfig.java     // 仅测试环境开启的身份注入
├── mapper/                               // AiKnowledge / AiIntent / AiChatRecord / AiUserMemory / BizBook / BizOrder
└── domain/                               // 实体与 DTO

ruoyi-ui/src/views/ai/
├── aichat/index.vue                      // AI 客服对话页
└── knowledge/index.vue                   // 知识库管理页

sql/                                      // 建库脚本、AI 表导出、Nacos 配置、向量迁移
docs/                                     // 项目文档（流程梳理等）
target/test_*.py                          // 自动化测试脚本
```

---

## 七、快速开始

### 7.1 环境要求

| 组件 | 版本 | 默认端口 | 说明 |
|---|---|---|---|
| JDK | 17+ | — | 编译与运行 |
| Maven | 3.8+ | — | 构建 |
| MySQL | 8.0+ | 3306 | 业务库 `ry-cloud` + Nacos 配置库 `ry-config` |
| Redis Stack | 7.x（含 RediSearch） | 6379 | 向量检索 + 缓存，**不能用普通 Redis** |
| Elasticsearch | 9.x | **9220** | 本机 9200/9300 可能被占用，按实际改 `ai.book.search.es-uri` |
| Nacos | 3.x | 8848 | 注册 + 配置中心 |
| Node.js | 14+ | — | 前端构建 |

### 7.2 初始化数据库

```bash
# 1) RuoYi 基础表（sys_* 共 20 张）
mysql -u root -p < sql/ry_20260417.sql

# 2) AI 客服及配套业务表（9 张，含结构 + 初始数据）
mysql -u root -p ry-cloud < sql/ai_customer_service_export_20261118.sql

# 3) AI 客服菜单（目录 + 页面，授权 admin）
mysql -u root -p ry-cloud < sql/ai_chat_menu.sql
```

> `sql/table_refactor_20261116.sql` 是历史整改 DDL（已包含在导出文件里），新环境**无需执行**。
> `sql/ry_config_20260611.sql` 会 **DROP 并重建 `ry-config` 库**，仅在初始化 Nacos 时使用，已有配置时不要跑。

### 7.3 配置 Nacos

1. 启动 Nacos（`8848`），确认 `ry-config` 库已就绪。
2. 新建配置：
   - **dataId**：`ruoyi-system-ai-dev.yml`
   - **group**：`DEFAULT_GROUP`
   - **格式**：YAML
   - **内容**：复制 [`sql/ruoyi-system-ai-dev.yml`](sql/ruoyi-system-ai-dev.yml)，把 `__ZHIPU_API_KEY__` 替换为真实智谱 Key。
3. 确认已存在 `application-dev.yml`、`ruoyi-system-dev.yml`（基础配置）。

> 改 Nacos 里的模型/密钥后需要**重启 ruoyi-system**：`ChatModel`/`EmbeddingModel` 在启动时构建，动态刷新不会重建。

### 7.4 启动后端

启动顺序：**MySQL / Redis / ES / Nacos → gateway → auth → system**。

```bash
# 方式一：Maven（pom 已固定 -Dfile.encoding=UTF-8）
mvn -pl ruoyi-gateway spring-boot:run
mvn -pl ruoyi-auth spring-boot:run
mvn -pl ruoyi-modules/ruoyi-system spring-boot:run

# 方式二：IDEA
# 直接运行各模块 main 方法；system 模块的 Run Configuration 必须加 VM option：
#   -Dfile.encoding=UTF-8
```

| 服务 | 端口 |
|---|---|
| ruoyi-gateway | 8080 |
| ruoyi-auth | 9200 |
| ruoyi-modules-system | **9201** |
| ruoyi-ui（dev） | 80 |

> ⚠️ **必须带 `-Dfile.encoding=UTF-8`**：Nacos 的 YAML 含中文注释，JDK 17 在中文 Windows 下默认 GBK，会抛 `MalformedInputException` 并被吞成「配置不存在」。这是本项目踩过的坑。

### 7.5 启动前端

```bash
cd ruoyi-ui
npm install
npm run dev        # http://localhost:80，代理到 8080
```

### 7.6 验证

```bash
# 健康检查（热门问题接口）
curl "http://127.0.0.1:9201/api/v1/chat/hot-questions?limit=5"

# 问答（本地直连需测试开关，见 11.4；经网关则带登录 token）
curl -X POST http://127.0.0.1:9201/api/v1/chat/question \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"question":"深入理解计算机系统多少钱"}'
```

---

## 八、接口清单

### 8.1 对话接口（`/api/v1/chat`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/question` | 用户提问（支持 `contextId` 多轮） |
| GET | `/profile/{userId}` | 画像快照（本人或管理员） |
| GET | `/records` | 当前登录用户对话记录（分页） |
| GET | `/hot-questions?limit=10` | 热门问题 TopN |
| DELETE | `/history/{contextId}` | 清空会话历史 |

### 8.2 管理接口

| 前缀 | 说明 | 权限 |
|---|---|---|
| `/api/v1/knowledge` | 知识库 CRUD | `ai:knowledge:*` |
| `/api/v1/book-content` | 正文上传 / 大纲 / 在线阅读 / 原件 | `biz:book:list` / `biz:book:edit` |
| `/api/v1/book-search` | ES 状态 / 手动重建 | `biz:book:edit` |
| `/biz/book` | 图书管理 | `biz:book:*` |
| `/biz/order` | 订单管理 | `biz:order:*` |

> 前端经网关访问时路径带 `/system` 前缀（网关转发时去掉）；直连 9201 时用 `/biz/...`。

---

## 九、数据表

| 表 | 说明 |
|---|---|
| `ai_knowledge` | 知识库标准问答（question / answer / category / hit_count） |
| `ai_intent` | 意图定义（intent_name / keywords） |
| `ai_chat_record` | 对话记录（user_id / session_id / user_question / ai_answer / chat_time） |
| `ai_user_memory` | 用户长期记忆（memory_type / content / keywords / hit_count） |
| `ai_user_profile` | 用户画像（profile_summary / tags / 提问计数 / 活跃时间） |
| `biz_book` | 图书（书名 / 作者 / 价格 / 库存 / 分类 / 上下架） |
| `biz_book_chunk` | 书籍正文分片（RAG 向量来源） |
| `biz_book_content` | 书籍正文文件（原文件路径 / 解析状态） |
| `biz_order` | 订单（user_id / book_id / book_title / status / 收货信息） |

订单状态：`0` 待付款、`1` 待发货、`2` 已发货、`3` 已完成、`4` 已退款。

---

## 十、测试

四套黑盒脚本共 **108 项**用例，覆盖接口、数据层、日志与降级路径：

| 套件 | 用例数 | 覆盖 |
|---|---|---|
| `target/e2e_full_test.py` | 33 | 核心链路：上传/分片/向量/FAQ 直达/缓存/意图/推荐/记忆/画像/异常 |
| `target/test_book_viewer.py` | 17 | 在线阅读：上传/目录/分页/PDF/扫描版降级/重传幂等 |
| `target/test_stock_es.py` | 18 | 库存与 ES：在售/下架/缺货/重建/停机降级/恢复 |
| `target/test_full_chain.py` | 40 | 三模块全链路：客服 18 + 订单 11 + 图书 11 |

```bash
# 需先起测试实例（带测试开关，避免依赖网关登录态）
java -Dfile.encoding=UTF-8 -cp "<classpath>" com.ruoyi.system.RuoYiSystemApplication \
  --server.port=9209 --ai.chat.test-security-bypass=true

E2E_BASE=http://127.0.0.1:9209 python target/test_full_chain.py
```

> 测试开关**仅本地直连实例开启**，生产必须保持 `false`。


## 十一、注意事项

### 11.1 大模型密钥

- 只放 Nacos（`ruoyi-system-ai-dev.yml`），**不要写进代码或 `application.yml`**。
- 仓库里的 `sql/ruoyi-system-ai-dev.yml` 是模板，Key 为 `__ZHIPU_API_KEY__` 占位符。

### 11.2 端口

- ES 默认 `9220`（本机 9200/9300 可能被 auth/file 占用），改 `ai.book.search.es-uri`。
- 前端 dev `80`，代理到网关 `8080`。

### 11.3 向量库

- Redis 必须是 **Redis Stack**（带 RediSearch），普通 Redis 无 `FT.CREATE` 会启动失败。
- 三个索引 `aa_knowledge_index` / `aa_book_index` / `aa_memory_index` 启动时自动创建（不存在才建）。
- 旧 `aa_rag_index` 仅作兼容保留，迁移脚本见 `sql/vector_split_migrate.py`。

### 11.4 测试开关

`ai.chat.test-security-bypass=true` 会按 `X-Test-User` 请求头注入登录态与全量权限，**仅供本地直连回归**，生产必须为 `false`（默认值）。

---

## 十二、开源说明

本项目基于 [RuoYi-Cloud](https://gitee.com/y_project/RuoYi-Cloud)（MIT License）二次开发，保留原有版权与许可证，遵循 [LICENSE](LICENSE)。

AI 智能客服相关模块（`com.ruoyi.system.{controller,service,search,config}` 下的 `Ai*` / `BookSearch*` / `RedisVectorConfig` 等）为本项目新增实现。
