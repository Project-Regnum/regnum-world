# v2.1 hardening validation (2026-11-17)

Passed on the existing feature branch:

* `JAVA_HOME=<Temurin 21.0.12> bash gradlew build`: full normal build,
  27 JUnit tests and 2 isolated dedicated-server GameTests passed. This includes
  all existing geometry, placement, persistence and regional growth tests.
* Combined `runGameTest` with local Nations 0.12.0 and Economy 1.0.0 JARs:
  2 required tests passed, including real Economy grant/receipt retry and full
  survival inventory rejection. Economy's original Nations artifact verifier was
  supplied its original JAR and passed; no packaged lock or JAR was altered.
* `git diff --check`: passed.

The combined run used these optional Gradle properties:

```
-Ploom_version=1.19.4
-PintegrationMods=../regnum-nations/build/libs/regnum-nations-0.12.0.jar,../regnum-economy/build/libs/regnum-economy-1.0.0.jar
-PintegrationNationsJar=../regnum-nations/build/libs/regnum-nations-0.12.0.jar
```

Those local JARs require newer Loom for development remapping. Loom 1.19.4 itself
required a Java 25 build JVM, so a temporary init script selected Java 21 for
compilation and JUnit; the committed GameTest task already selects Java 21. The
normal final build used Java 21 throughout with the unchanged Loom 1.14.10 pin.

Focused checks cover failed and partial spawn recovery, no victory on boss
absence, collision-safe live spawning, repeated boss spawn requests, serialized
cooldowns, mailbox exclusive delivery fences and payload retention, frozen loot,
held claims across saved-state reload/removal, nonce expiry/replacement, immutable
controller discovery, recursion prevention and exception-safe replay scopes.

All server worlds were disposable `build/run/gameTest/world` data. No production
world was opened. Persistence tests reload serialized data; they do not simulate
an OS crash or power loss. Multiplayer races, live Nations claim workflows,
client-side PvP, combat balance and player transfers during real crash recovery
remain playtest work. Passing these checks is not a claim of full compatibility.
