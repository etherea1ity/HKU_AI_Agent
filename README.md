# HKU AI Agent 本地运行说明

以下步骤经过实机验证，可在 Windows 环境下启动后端 Spring Boot 服务与前端 Vite 应用，确保页面能够正常访问。

## 环境准备
1. Java 21 (推荐通过 `winget install --id Microsoft.OpenJDK.21 --source winget` 安装)。
2. Node.js 18 及以上版本（已验证 `v18.18.2` 可用）。
3. Maven 与 npm 已随项目附带的 `mvnw` 和前端依赖满足，无需额外安装。

> 如果此前已安装过 JDK8，需要把 `JAVA_HOME` 指向 JDK21，并把 `%JAVA_HOME%\bin` 置于 `PATH` 最前面，否则 Spring Boot 插件会因 class file 版本过低而退出。

## 后端启动
1. 打开 PowerShell，进入项目根目录：

2. 启动 Spring Boot 应用（已关闭测试用例，加快启动速度）：
	```powershell
	.\mvnw.cmd spring-boot:run -DskipTests
	```
4. 控制台出现 `Tomcat started on port 8123` 后表示启动成功，接口可通过 `http://localhost:8123/api` 访问。保留该终端以保持后端运行，如需停止按 `Ctrl+C`。

### RAG 功能开关
1. 默认将 `hku.ai.rag.enabled` 设为 `false`，这样在没有有效 DashScope API 密钥时也能成功启动。
2. 若需要启用 RAG，修改 `src/main/resources/application.yml`：
  ```yaml
  hku:
	 ai:
		rag:
		  enabled: true
  ```
3. 同时把 `spring.ai.dashscope.api-key` 替换为有效密钥，并重启后端。

## 前端启动
1. 在新的 PowerShell 会话进入前端目录：
	```powershell
	cd hku-ai-agent-frontend
	```
2. 安装依赖（第一次必需，后续如无 package 变更可跳过）：
	```powershell
	npm install
	```
3. 启动 Vite 开发服务器：
	```powershell
	npm run dev -- --host 0.0.0.0 --port 5173
	```
4. 浏览器访问 `http://localhost:5173/` 即可看到页面。当前仅验证展示层可用，前端按钮请求的后端接口仍待后续改造。

> 若出现 npm 安全告警，可按需执行 `npm audit fix` 或 `npm audit fix --force`。这些告警不会影响页面展示。

## 运行问题
1. 如需永久生效的 `JAVA_HOME`，可在“系统属性 -> 环境变量”中设置同样的路径，并将 `%JAVA_HOME%\bin` 移到用户 Path 的首位。
2. 每次修改配置后，建议重新执行对应的启动命令，确认控制台无异常告警再进行下一步操作。
3. 若要同时运行前后端，可保持后端窗口不动，另开一个终端启动前端；需要全部结束时依次 `Ctrl+C` 退出两个进程。

# Todo

## 前端改版（较多一点）
1. 主要修改 `hku-ai-agent-frontend/src`（App.vue、router、views、components 等）
2. 这里尽量在不动接口的情况下，重新排版整个界面，删去不必要的外站链接
3. 重写导航与聊天 UI，可以新增一些关于HKU信息，比如地图，天气，时间的展示
4. 去除恋爱主题并展示检索信息

## HKU 小助手，也就是替换原恋爱助手（人得多点）
1. 主要修改 `src/main/java/com/hku/hkuaiagent/app` 与 `controller`
2. 重命名并调整 Prompt/返回格式，使其服务于HKU课程/规章/校历/新闻
3. 与前端约定接口路径/参数/返回结构，提供示例 payload

## RAG 数据管线
1. 主要改写 `src/main/java/com/hku/hkuaiagent/rag` 与 `src/main/resources/document`，让RAG能正常使用
2. 替换文档数据、优化切分与向量存储以支撑 HKU 资料检索
3. 收集相关资料，填入检索库中

## 全能 Agent 与（只用微调）
1. 调整 `src/main/java/com/hku/hkuaiagent/agent` 与 `tools`，重写全能 Agent 提示词并筛选/新增适合校园场景的工具集合

## MCP 与外部服务工具（需自行查询相关api和调用方式）
1. 更新 `hku-image-search-mcp-server` 及主项目 `mcp-servers.json`，主要功能写在'imageSearchTool.java'
2. 注意，这里需要api，需要自行检索一下怎么调用MCP。
3. 配置图像检索等 MCP 工具并确保主服务可调用。
4. bonus，可以甚至加入一些天气信息tool，或者其他简单的tool，让其更适配HKU信息

这里附一条AI生成的实现路径：
1. 核心实现在 ImageSearchTool.java。
2. 需要把示例里的 API_KEY 换成真实服务（Pexels 或你选的其它图像 API），按需扩展请求参数和返回字段。
M3. CP 服务启动后，由主项目的 mcp-servers.json 声明连接；主服务里通过 Spring AI 的 MCP 客户端把 searchImage 暴露给大模型使用。
4. 调试时要同时起动 MCP 子项目（.[mvnw.cmd](http://_vscodecontentref_/4) spring-boot:run）和主后端，验证 MCP handshake 与工具输出是否按预期返回图片 URL。

以及检查一下PDF导出工具：
1. 对应 README 的 全能 Agent 与工具 任务；实现类在 PDFGenerationTool.java。
2. 该工具使用 iText 生成 PDF，文件默认写入 FileConstant.FILE_SAVE_DIR（即项目根目录下的 tmp/pdf 路径）；必要时替换字体或调整保存目录。
3. 所有可用工具在 ToolRegistration.java 统一注册，PDFGenerationTool 会随 agent 创建一起注入；调整工具集合或提示词时记得同步这里。
4. 若前端需要下载生成的 PDF，可复用同目录下的 ResourceDownloadTool 或新增专用接口，把 tmp/pdf 下的文件暴露出去。

## 配置与文档
同步修改 `application.yml`、环境变量、README 与测试脚本，完善 Key 管理、接口契约及端到端验证流程。

## 后端部分更新

### tools更新
1. `com.hku.hkuaiagent.tools`下的8个工具，经修改测试可正常运行。再次本地测试可在src目录下的test/tools下逐个进行验证。搜索的API key要在`ToolRegistration`和`application.yml`中更新

### MCP更新
1. 两个MCP，分别是调用高德地图和图片搜索。高德地图MCP通过本地stdio运行，在`mcp-servers.json`中已配置，需要修改文件中command路径为自己的
2. 图片搜索MCP在`ImageSearchTool`中实现，需要修改文件中的API_KEY，同时在`hku-image-search-mcp-server`包下的resources目录，为图片搜索MCP配置项。*由于这里`application.yml`开启的是本地stdio服务，jar包已在`hku-image-search-mcp-server`目录的target目录下打包好，因此项目启动时不需要额外运行MCP子项目，在`mcp-servers.json`中已配置好
3. 要在src下的`aplication.yml`加入读取`mcp-servers.json`的配置

测试：`huManusTest`debug启动，并在MyLoggerAdvisor的before方法的return上打断点，若看到`toolCallbacks`的size为21，则说明tools和MCP调用成功。
