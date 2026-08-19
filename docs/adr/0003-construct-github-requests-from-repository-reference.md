# 只从仓库引用构造 GitHub 请求

Yuki-RepoGuide 不直接访问用户提交的原始 URL，而是仅接受主机名严格为 `github.com` 的 HTTPS 公开仓库地址，从中解析并校验 `owner/repo` 仓库引用，再由后端构造固定 `api.github.com` 请求。仓库子页面、`.git` 后缀、查询参数与锚点可以被规范化，但 GitHub Enterprise、私有仓库、自建 Git 服务和其他远程地址不属于 V0 范围。这个限制牺牲了部分输入兼容性，却能阻止任意 URL 获取造成的 SSRF，并让 GitHub 接入保持可验证、可测试。
