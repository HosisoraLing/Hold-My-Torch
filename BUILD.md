# 构建与开发

只面向 Windows / Linux 的 Minecraft Java 客户端。Forge 使用 JDK 17，NeoForge 使用 JDK 21；无需安装全局 Gradle，仓库包含 Gradle 8.8 Wrapper。首次构建需要网络，后续复用 Gradle 缓存。不要把构建输出、游戏运行目录或缓存加入版本控制。

## Windows

将 `JAVA_HOME` 指向对应 JDK 后，在项目根目录运行：

```powershell
# Java 17
.\gradlew.bat -p forge build
# Java 21
.\gradlew.bat -p neoforge build
```

## Linux

使用对应 JDK 后，在项目根目录运行：

```sh
sh gradlew -p forge build
sh gradlew -p neoforge build
```

产物：

- `forge/build/libs/hold-my-torch-0.1.2-mc1.20.1-forge.jar`，ForgeGradle 执行 `reobfJar` 后才是发布文件；不要把未重映射的中间文件拿去安装。
- `neoforge/build/libs/hold-my-torch-0.1.2-mc1.21.1-neoforge.jar`。

JAR 采用 `模组名-模组版本-mc游戏版本-加载器.jar` 命名。

固定构建基线：Forge **47.3.0** / ForgeGradle **6.0.36**，NeoForge **21.1.228** / ModDevGradle **2.0.141**。这些基线不等于已经逐个测试全部 47.x / 21.1.x 小版本。

如果 Forge 官方 Maven 返回 403，可显式加 `-PforgeMirror` 启用 BMCLAPI 只读依赖镜像；默认构建仍使用官方仓库。Java / Gradle 未自动继承系统代理时，按本机代理配置给该次构建传递 JVM 代理参数即可，不需要修改系统网络设置。镜像或本机代理不是 Mod 的运行时依赖。

## API 依据

- [Forge 1.20.1 KeyMapping 补丁](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/client/KeyMapping.java.patch)
- [NeoForge 1.21.1 KeyMapping 补丁](https://github.com/NeoForged/NeoForge/blob/1.21.1/patches/net/minecraft/client/KeyMapping.java.patch)
- [Forge 1.20.1 客户端交互补丁](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/client/multiplayer/MultiPlayerGameMode.java.patch)
- [NeoForge 1.21.1 客户端交互补丁](https://github.com/NeoForged/NeoForge/blob/1.21.1/patches/net/minecraft/client/multiplayer/MultiPlayerGameMode.java.patch)
- [RyoamicLights 1.20.1 可选亮度接口](https://github.com/ThinkingStudios/RyoamicLights/blob/1.20.1-architectury/common/src/main/java/org/thinkingstudio/ryoamiclights/RyoamicLights.java)

开发时还应检查本次构建生成的映射后 Minecraft 源码，不要仅根据客户端物品放置事件推断服务器是否收到使用数据包。
