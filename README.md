# Ev's Mod for Minecraft 1.21.11

Fabric client-side utility mod for Minecraft 1.21.11.

## Download

Download the ready-to-use mod jar committed in this repository:

[Download Ev's Mod-1.21.11-2.1.jar](build/libs/Ev%27s%20Mod-1.21.11-2.1.jar)

You can also download builds from the releases page when releases are published:

[GitHub Releases](../../releases)

## Install

1. Install Minecraft 1.21.11 with Fabric Loader.
2. Use Java 21.
3. Install the required runtime mods:
   - Fabric API for Minecraft 1.21.11
   - MaLiLib for Minecraft 1.21.11
4. Put `Ev's Mod-1.21.11-2.1.jar` into your Minecraft instance `mods` folder.
5. If you use Baritone-backed features, also install a compatible Fabric Baritone jar.
6. Start the game.

## Build From Source

This branch targets Minecraft 1.21.11 and Java 21.

```powershell
java -version
.\gradlew.bat clean build --stacktrace
```

The built jar will be in:

```text
build/libs/
```

## Compatibility

- Minecraft: 1.21.11
- Java: 21
- Mod loader: Fabric
- Required mods: Fabric API, MaLiLib
- Optional feature dependency: Baritone Fabric
