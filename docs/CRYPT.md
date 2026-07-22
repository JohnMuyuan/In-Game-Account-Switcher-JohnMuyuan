# 全局密码库与账号数据加密

In-Game Account Switcher: JohnMuyuan Edited Version 使用一个全局密码保护保存在游戏内账号切换器中的 Microsoft 账号。本文描述当前版本的实际存储格式、加密参数和安全边界。

## 全局密码模型

- 首次添加 Microsoft 账号前，用户必须创建全局密码。
- 后续添加账号、解锁密码库、切换账号和修改全局密码时，使用同一个全局密码。
- 全局密码只保存在当前游戏进程的内存中，不会以明文形式写入磁盘。
- 忘记全局密码后无法恢复账号数据，也不存在后门或找回密钥。只能删除密码库并重新添加账号。

## 密码要求

创建或修改全局密码时，密码必须同时满足以下条件：

- 长度大于 16 个字符；
- 至少包含一个大写字母；
- 至少包含一个小写字母；
- 至少包含一个数字；
- 至少包含一个特殊字符。

不要把 Microsoft 账号密码用作全局密码，也不要把全局密码发送给任何人。

## 文件位置

密码库保存在 Minecraft 游戏目录下：

```text
_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/
└── .hidden/
    ├── accounts_v1.do_not_send_to_anyone
    └── global_password_v2
```

- `accounts_v1.do_not_send_to_anyone` 保存账号记录和加密后的 Microsoft 凭据。
- `global_password_v2` 保存使用全局密码加密的固定校验标记，用于判断输入的密码是否正确。
- 这些文件包含敏感账号数据，不应上传、分享或提交到 Git 仓库。

## 加密参数

全局密码通过以下参数派生账号加密密钥：

- 密钥派生算法：`PBKDF2WithHmacSHA512`
- 迭代次数：`1,000,000`
- 派生密钥长度：256 位
- 对称加密算法：`AES/GCM/NoPadding`
- GCM 认证标签：128 位
- 每次加密使用 128 字节随机盐
- 每次加密使用 16 字节随机 IV
- 随机数来源：`SecureRandom.getInstanceStrong()`

盐和 IV 不需要保密，会与密文一起保存。AES-GCM 的认证标签用于检测密文被篡改或使用了错误密码。

## 被保护的数据

Microsoft 账号的 access token 和 refresh token 会使用全局密码派生的密钥加密。账号名称、UUID、账号类型等用于展示和识别记录的外围元数据并不是整个文件容器级加密，因此不应把账号文件视为完全匿名的数据。

离线账号不包含 Microsoft access token 或 refresh token，不能提供与在线 Microsoft 账号相同的凭据保护含义。

## 修改全局密码

修改密码时，模组会以事务方式重新加密账号数据：

1. 为账号文件和密码校验器创建 `.password_change_backup` 备份。
2. 写入 `.hidden/password_change_pending` 标记。
3. 使用新密码重新加密所有受保护账号数据。
4. 保存新的密码校验器。
5. 成功后删除 pending 标记和备份文件。

如果游戏在换密过程中异常退出，下次启动会检测 pending 标记并从备份恢复。没有 pending 标记的陈旧换密备份会被清理。

## 安全边界

此设计主要防止密码库文件被复制、泄露或从磁盘备份中取得后，攻击者直接读取 Microsoft token。它不能防御已经能控制游戏进程或系统的恶意软件，包括键盘记录、调试器、进程内存读取、恶意模组或已取得当前 Windows 用户权限的攻击者。

使用可信模组来源，保护 Minecraft 游戏目录和系统账号，并为全局密码使用唯一且足够复杂的内容。
