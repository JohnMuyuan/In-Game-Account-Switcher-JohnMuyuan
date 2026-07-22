# In-Game Account Switcher: JohnMuyuan Edited Version

[English](README.md) | **简体中文**

![In-Game Account Switcher 图标](ias.png)

允许您在游戏中更改登录的账号，而无需重新启动 Minecraft。此版本为江木源（JohnMuyuan）修改版，添加了多项功能并优化了账号切换体验。

本项目是 [In-Game Account Switcher](https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher) 的非官方第三方修改版本。原项目作者、贡献者与 LGPL-3.0-or-later 许可信息均予以保留。

## 支持范围

当前已验证的构建目标：

| Loader | Minecraft | Java | 其他要求 |
| --- | --- | --- | --- |
| Fabric | `1.20.1` | Java 17 | Fabric Loader `0.19.1+`、Fabric API；Mod Menu 可选 |
| Fabric | `26.1.2` | Java 25 | Fabric Loader `0.19.1+`、Fabric API；Mod Menu 可选 |
| Fabric | `26.2` | Java 25 | Fabric Loader `0.19.1+`、Fabric API；Mod Menu 可选 |
| Forge | `1.20.1` | Java 17 | Forge `47.3.19` 至 `<48` |
| Forge | `26.1.2` | Java 25 | Forge `64.x` |
| Forge | `26.2` | Java 25 | Forge `65.x` |

此仓库不声明支持 NeoForge、Quilt 或上表以外的组合。

## 主要修改

- 重新设计主菜单与游戏内账号切换器的左右分栏界面。
- 优化账号列表、按钮布局、窗口缩放与皮肤预览自适应。
- 支持鼠标拖拽旋转角色，并在打开二级界面时保留预览姿势。
- 改进皮肤与披风的预加载、缓存、切换状态和刷新逻辑。
- 使用一个全局密码保护全部已保存的 Microsoft 账号，不再为每个账号设置独立密码。
- 支持在已解锁的账号库中安全修改全局密码，并在操作中断时尝试恢复原数据。
- 移除了连接上游仓库的远程停用控制。
- 收紧本地 OAuth 回调服务器和认证错误日志的敏感数据保护。

## 安装

1. 安装上表中的 Minecraft 版本、对应 loader，以及 Fabric 构建所需的 Fabric API。
2. 如果已经安装原版 In-Game Account Switcher，请先从 `mods` 文件夹移除它。
3. 将版本和 loader 匹配的 JAR 放入 Minecraft 的 `mods` 文件夹。

本修改版继续使用模组 ID `ias`，因此不能与原版 IAS 同时安装。

## 从源码构建

使用仓库自带的 Gradle Wrapper。Gradle 本身应由 JDK 25 启动；构建 Minecraft 1.20.1 时还需要本机已有的 JDK 17 toolchain。

Windows 示例：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"
./gradlew.bat --no-daemon "-Dru.vidtu.ias.only=26.2-fabric" :26.2-fabric:clean :26.2-fabric:check :26.2-fabric:assemble
```

Linux/macOS 示例：

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew --no-daemon \
  -Dru.vidtu.ias.only=26.2-fabric \
  :26.2-fabric:clean :26.2-fabric:check :26.2-fabric:assemble
```

将 `26.2-fabric` 替换为支持表中的任一目标。生成的可发布 JAR 位于：

```text
build/libs/IAS-9.0.7-johnmuyuan.1+<Minecraft版本>-<loader>.jar
```

首次构建需要下载 Minecraft、loader 和构建依赖；离线模式仅在所需依赖已经缓存时可用。

## 账号库安全

首次保存 Microsoft 账号时，模组会要求创建全局密码。密码必须超过 16 个字符，并同时包含大写字母、小写字母、数字和特殊字符。请勿使用 Microsoft 账号密码，也不要把全局密码发送给任何人。

全局密码用于保护磁盘上的账号文件。它能够提高账号文件被复制或泄漏后的破解成本，但不能防御已经控制电脑、能够读取游戏进程内存、键盘输入或屏幕内容的恶意软件。密码只在当前游戏进程解锁后保留于内存，不会以明文写入账号文件。

全局密码无法找回。忘记密码后，只能删除密码库和已保存的账号数据，再重新添加账号。详细格式、算法、文件位置与威胁模型见 [docs/CRYPT.md](docs/CRYPT.md)。

**绝对不要分享游戏目录中的 `_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE` 文件夹，即使它看起来是空的。不要公开未经脱敏的游戏日志。**

## 许可与署名

本项目依据 [GNU Lesser General Public License v3.0 or later](LICENSE) 发布。

- 原始作者：VidTu
- 修改版作者：JohnMuyuan
- 原始项目及其他贡献者信息保留在模组元数据和源码头部中
- 本修改版的相关修改由 JohnMuyuan 于 2026 年完成
- 修改版图标基于原项目图标，经生成式 AI 辅助修改；原图及修改版随本项目依照 LGPL-3.0-or-later 提供

Minecraft 是 Mojang Studios 的商标。本项目与 Mojang Studios 或 Microsoft 无隶属关系。
