# 项目一句话介绍

对应 GitHub Issue #8。基础导览先展示；随后普通 HTTP 请求生成中文介绍，不使用轮询或流式连接。README 图片辅助理解保留在后续 Issue #21。

## 数据流

Controller 将当前 Session 的归属标识交给 GuideService。服务从已读取的 metadata 与 README 提取有限文本和证据，保存为临时快照，并返回 explanationInputId。

第二次请求通过该标识取出同一份资料。介绍生成模块调用内部模型 port；DeepSeek adapter 负责 HTTP 协议，不能读取 Session 或快照存储。Java 校验输出后才返回介绍。有效证据引用只能证明来源可追溯，不能证明介绍的自然语言含义已被核实。

## HTTP 契约

- POST /api/guides：原响应新增 explanationInputId。资料仍可正常展示；若存储已满且所有快照都在生成中，标识为 null，页面提示重新生成导览。
- POST /api/guides/explanation：请求为 {"explanationInputId":"UUID"}，受 Session 和 CSRF 保护。
- 200：status 为 AVAILABLE、INSUFFICIENT_EVIDENCE 或 UNAVAILABLE，附 introduction、evidenceIds、evidence、code、retryAfterSeconds。技术失败只影响解释区域；可靠的上游限流秒数通过 retryAfterSeconds 返回。
- 400 / INVALID_EXPLANATION_INPUT：标识缺失或格式无效。
- 410 / EXPLANATION_INPUT_EXPIRED：快照不存在、已过期、已淘汰或不属于当前 Session；不区分这些情况，也不自动重新读取 GitHub。
- 409 / EXPLANATION_IN_PROGRESS：同一快照已有一轮生成，重复请求不会触发新模型调用。
- 401 / 403：沿用试用访问与 CSRF 保护。重新认证不会转移旧 Session 的快照。

UNAVAILABLE 的 code 区分 EXPLANATION_NOT_CONFIGURED、EXPLANATION_TIMEOUT、EXPLANATION_RATE_LIMITED、EXPLANATION_UPSTREAM_FAILURE、EXPLANATION_INVALID_OUTPUT。页面采用安全的通用文案，服务端只记录受控错误码，不记录密钥、Session、完整 README、提示词或上游正文。

README 局部重试成功后，页面会使旧介绍及其迟到响应失效；用户需要重新生成导览取得新介绍。失败的 README 重试不会删除旧介绍。

## 配置

所有配置位于 application.properties，可通过部署配置覆盖。

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| YUKI_DEEPSEEK_API_KEY | 空 | 可选的服务端凭据；缺失不阻止基础导览 |
| yuki.explanation.snapshot-ttl | 10m | 从创建时算起，不因重试延长 |
| yuki.explanation.capacity | 128 | 单进程快照数量上限 |
| yuki.explanation.generation-timeout | 30s | 首次调用与一次纠正重试共享的总预算 |
| yuki.explanation.max-input-characters | 12000 | 仓库名、描述及所选 README 文本的字符预算 |
| yuki.explanation.max-introduction-characters | 300 | 输出介绍的 Unicode 码点上限 |
| yuki.explanation.max-response-bytes | 32768 | 单次模型 HTTP 响应的字节上限 |

快照按创建顺序淘汰非活动条目，不淘汰正在生成的条目；生成结束后释放占用并清理已过期快照。重启会丢失全部快照。本票不缓存模型结果，用户手动重试会重新调用模型。

适配器固定访问 https://api.deepseek.com/chat/completions，模型名为 deepseek-flash，禁用思考模式、流式响应和工具输入，使用 JSON Output；不跟随重定向。只有输出内容校验无效才纠正重试一次，超时或上游故障不会自动重试。

## 验证与人工接入检查

普通测试使用 fake 模型 port、本地 HTTP 模拟服务和可控时钟，不需要真实 DeepSeek 凭据。

- 后端：在 backend 运行 ./mvnw.cmd test。
- 前端：在 frontend 运行 npm.cmd test 和 npm.cmd run build。
- 人工接入检查：由操作者在运行环境安全配置凭据并启动应用，选择有明确文本介绍的公开仓库；观察基础事实先展示、介绍随后到达，并展开证据核对。不要把凭据写入源码或日志。真实供应商调用不属于普通自动化测试。

协议依据：[DeepSeek 模型更新](https://api-docs.deepseek.com/zh-cn/updates/)、[JSON Output](https://api-docs.deepseek.com/guides/json_mode)、[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion)。
