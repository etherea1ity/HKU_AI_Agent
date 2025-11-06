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
	cd C:\Users\jsj31\Desktop\HKU\7606NLP\yu-ai-agent\hku-ai-agent-frontend
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
