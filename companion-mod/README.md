# MoonCore Companion

Optional Fabric client mod for MoonCore servers.

The server still works for Java vanilla and Bedrock players. Players with this mod get the v2
`mooncore:companion` protocol: chunked rig/animation payloads, client-side skeletal playback, armor
metadata storage, and client-side hiding of vanilla fallback BlockDisplay bones when the server
sends their UUIDs.

## Build

Requires JDK 21.

```bash
cd companion-mod
gradlew.bat build
```

This workspace currently has no Gradle wrapper files checked into `companion-mod/`; use an installed
Gradle or generate the wrapper once if needed. The default build target is Minecraft `1.21.11` with
Yarn `1.21.11+build.6` and Fabric API `0.141.3+1.21.11`.

The mod metadata accepts Minecraft `>=1.21.1 <=1.21.11`. To test a 1.21.1 build, override the
properties:

```bash
gradle build -Pminecraft_version=1.21.1 -Pyarn_mappings=1.21.1+build.3 -Pfabric_version=0.105.0+1.21.1
```

## Protocol

Legacy v1:

- C2S `HELLO`: `[0x01, protocol]`
- S2C `WELCOME`: `[0x02, protocol, capabilities]`

v2 payloads are chunked S2C messages:

- `0x10 PUSH_RIG`
- `0x11 PUSH_ANIM`
- `0x12 PLAY_ANIM`
- `0x13 PUSH_ARMOR`

Each chunk starts with:

```text
opcode u8, protocol u8, transferId uuid(2 longs), chunkIndex u16,
chunkCount u16, totalLength i32, chunkLength i32, bytes[chunkLength]
```
