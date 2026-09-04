MCP URL:       http://192.168.76.141:30812/pf-devkit/ai-plugin/mcp/sse
Health URL:    http://192.168.76.141:30812/pf-devkit/ai-plugin/mcp/health
Ready URL:     http://192.168.76.141:30812/pf-devkit/ai-plugin/mcp/ready
Auth:          Bearer 6b9ff858f79eb09029aa627e6e210b32bcdfdfa44aeb93fb87dd3a73510c990b（GET /sse 与 POST /message 均须携带）
Workspace:     私有化单工作空间，不需要FH_SERVER_ADDRESS、 FH_ACCESS_TOKEN 、 FH_WORKSPACE_UUID 等服务端参数
Allowed security level: L2（查询+设备操作+飞行任务；L3 紧急类禁用）
Tools:         53 个 fh_* 工具（连接后 tools/list 自取完整 Schema；危险操作支持 dry_run+confirm）
Rate limit:    100 req / 60s / IP；会话超时 1800s；飞控权全局互斥（同一时刻仅一个操作者）
Network:       内网直连 192.168.76.141:30812（需 VPN/内网路由），HTTP 明文，无需客户端证书
Versions:      MCP 1.0.0-SNAPSHOT / 上云API2 SDK 3.1.0.0 / 镜像 pf-devkit-mcp:3836486
Test scope:    先 fh_device_list / fh_wayline_status 查询 → dry_run 演练 → 真实任务
               设备: Dock3 8UUXN2K00A0144 (M4TD 1581F8HGX252500A009X)