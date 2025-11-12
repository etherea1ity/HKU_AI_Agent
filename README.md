## 后端部分更新

### tools更新
1. `com.hku.hkuaiagent.tools`下的8个工具，经修改测试可正常运行。再次本地测试可在src目录下的test/tools下逐个进行验证。搜索的API key要在`ToolRegistration`和`application.yml`中更新

### MCP更新
1. 两个MCP，分别是调用高德地图和图片搜索。高德地图MCP通过本地stdio运行，在`mcp-servers.json`中已配置，需要修改文件中command路径为自己的
2. 图片搜索MCP在`ImageSearchTool`中实现，需要修改文件中的API_KEY，同时在`hku-image-search-mcp-server`包下的resources目录，为图片搜索MCP配置项。*由于这里`application.yml`开启的是本地stdio服务，jar包已在`hku-image-search-mcp-server`目录的target目录下打包好，因此项目启动时不需要额外运行MCP子项目，在`mcp-servers.json`中已配置好
3. 要在src下的`aplication.yml`加入读取`mcp-servers.json`的配置

测试：`huManusTest`debug启动，并在MyLoggerAdvisor的before方法的return上打断点，若看到`toolCallbacks`的size为21，则说明tools和MCP调用成功。
