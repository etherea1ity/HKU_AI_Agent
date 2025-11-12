## 后端部分更新c

### tools更新
1. `com.hku.hkuaiagent.tools`下的8个工具，经修改测试可正常运行。再次本地测试可在src目录下的test/tools下逐个进行验证。搜索的API key要在`ToolRegistration`和`application.yml`中更新

### MCP更新
1. 两个MCP，分别是调用高德地图和图片搜索。高德地图MCP通过本地stdio运行，在`mcp-servers.json`中已配置，需要修改文件中command路径为自己的
2. 图片搜索MCP在`ImageSearchTool`中实现，需要修改文件中的API_KEY，同时在`hku-image-search-mcp-server`包下的resources目录，为图片搜索MCP配置项。*由于这里`application.yml`开启的是本地stdio服务，jar包已在`hku-image-search-mcp-server`目录的target目录下打包好，因此项目启动时不需要额外运行MCP子项目，在`mcp-servers.json`中已配置好
3. 要在src下的`aplication.yml`加入读取`mcp-servers.json`的配置

测试：`huManusTest`debug启动，并在MyLoggerAdvisor的before方法的return上打断点，若看到`toolCallbacks`的size为21，则说明tools和MCP调用成功。


## 🎯 2025.11.12 19:00

### ✨ 从无法对话到优雅交互

**之前的问题：**
- ❌ 前端显示转义的JSON文本（\"、\n到处都是）
- ❌ 没有自然语言回复，只有后端控制台输出
- ❌ 对话体验混乱

**现在的体验：**
- ✅ 流式打字机效果的自然对话
- ✅ 智能过滤技术细节，只显示用户友好内容
- ✅ 完整的问答体验

### 🧠 对话记忆系统

- **多轮对话支持**：AI能记住之前的对话内容
- **历史对话管理**：
  - 📋 查看所有历史对话
  - ➕ 创建新对话
  - 🔄 切换不同对话
  - 💾 自动保存对话记录
  
### 🤔 深度思考可视化

不再是黑盒！现在可以看到AI的思考过程：

[HkuManus深度思考中...] 🔄
├─ 第1步：调用地图搜索工具
├─ 第2步：调用路线规划工具
└─ [思考完成] ✓
### 📄 PDF生成和下载**完整的PDF功能：**
1. 生成包含中文的PDF文档
2. 支持Markdown格式（标题、粗体）
3. 一键下载（美观的紫色按钮）
4. 自动处理中文文件名
**使用示例：**
你："帮我生成一个PDF，里面写了12345和上山打老虎"
AI：生成完成！[📥 点击下载PDF] ← 点击即可下载


### 🎨 用户体验优化

- 紫色渐变主题设计
- 平滑的动画效果
- 响应式布局
- 智能的emoji处理
