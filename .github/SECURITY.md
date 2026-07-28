# In-Game Account Switcher (JohnMuyuan 修改版) - 安全政策

## 支持的版本

仅对最新发布的版本（GitHub Releases 页面上标记为 Latest 的版本）提供安全修复。旧版本不接收安全更新。

## 报告安全漏洞

**请勿通过公开的 GitHub Issue 报告安全漏洞。** 账户切换类模组涉及账号凭据与令牌存储，公开披露可能被恶意利用。

请通过以下方式私下报告：

1. 使用 GitHub 的 [Private vulnerability reporting](https://github.com/JohnMuyuan/In-Game-Account-Switcher-JohnMuyuan/security/advisories/new) 功能提交报告；
2. 或在 GitHub 上私信仓库所有者。

报告中请尽量包含：

- 漏洞影响的版本与构建目标（Minecraft 版本 / 加载器）；
- 复现步骤；
- 潜在影响（如本地凭据泄露、令牌暴露等）。

## 响应承诺

- 我会在收到报告后尽快确认（通常在 7 天内）；
- 修复完成后会发布新版本并致谢报告者（如报告者愿意）。

## 范围说明

本模组会在本地存储 Microsoft 账户令牌（加密保存，详见 [docs/CRYPT.md](../docs/CRYPT.md)）。以下情况**不属于**本项目的安全漏洞范畴：

- 已控制用户计算机的恶意软件（可读取进程内存、键盘输入或屏幕内容）；
- 用户主动分享 `_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE` 目录或全局密码导致的问题；
- 上游原版模组（The-Fireplace / VidTu）独有、与本修改版无关的问题——请向其上游仓库报告。
