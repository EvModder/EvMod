# Ev's Mod

My custom client, with a focus on Mapart.<br>
Copying, collection sorting, img export, visual details, and misc utilities.

Usage details are over [on Modrinth](https://modrinth.com/mod/evmod).

## Supported versions

- Minecraft 1.21.4
- Minecraft 1.21.11
- Minecraft 26.2
- Minecraft 26.3

## Build commands

Standard Gradle build

```sh
./gradlew build
```

Build and collect all JARs under build/\<version>/:

```sh
./gradlew buildAndCollect
```

Build only for a specific version:

```sh
./gradlew :1.21.4:buildAndCollect
```