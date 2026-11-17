# Dungeon v2.1 operations and integration

## Encounter recovery

A missing UUID is not a death. Recovery runs only after all dungeon terrain and
entity chunks are loaded, and retries use the configured spawn cooldown (at least
30 seconds). Missing bosses never grant victory, rewards, or reset cleared rooms.
Zero accepted spawns immediately release `started` and establish a cooldown.
Partial spawns form a smaller encounter: accepted survivors must die before the
room clears; missing members are not replenished while survivors remain. All
mutation is on the server thread, including simultaneous player entry.

Existing cycle tags reject stale mobs after reset/removal; ownership tags also
clean loaded projectiles. Unloaded projectiles are rejected on their next load.
Ordinary tick processing does not request dungeon chunk tickets. Administrative
placement has bounded, released tickets. Active timestamps dirty saved data at
most every 30 seconds rather than every second. Retired rewards are polled every
five seconds; offline claims do not perform delivery-marker filesystem probes.

## Standalone reward mailbox and crash boundary

Players use `/regnum rewards` and `/regnum rewards claim <full claim UUID>`.
Claims are owner-bound, survive offline periods, reset and unregister, and freeze
actual item NBT before delivery. A full inventory leaves the entire claim pending;
items are never dropped. Standalone mode awards items only; coins require Economy.
Existing pending claims migrate lazily when their player is available.

Before any standalone inventory mutation, World creates an exclusive delivery
marker under `<world>/regnum-reward-deliveries/<stable key>.snbt`. Its key derives
from dungeon UUID, cycle, room (if any), and player UUID. The marker contains the
claim's full loot, player, dungeon and receipt. File and directory entries are
forced to storage before inventory mutation. A preexisting marker blocks ALL
retries, including switching to Economy. Markers remain as delivery tombstones.
If marker creation or forcing fails, inventory is untouched.

Minecraft inventory mutation and SavedData are **not one atomic transaction**.
The uncertain interval begins when the marker is created and ends after inventory
insertion, the attempted player save and the attempted claim removal save. A
crash in that interval can leave zero, some, or all items saved. Minecraft's player
save can also log an I/O error without propagating it. In either case the marker
prevents automatic duplication and retains evidence, including when pending
removal was saved but player data was not. Completed markers do not prove player
save success. This is an at-most-once automatic attempt, with manual recovery of
uncertain outcomes; it is not guaranteed exactly-once item delivery.

For reconciliation, stop the server and back up both playerdata and dungeon data.
Compare the marker payload with the affected player's saved inventory and any
transfer/death records before granting missing items manually. Never simply delete
markers or clear `DeliveryHeld`: items may already have been saved or transferred.
There is deliberately no automatic unlock/retry command. Keep markers and dungeon
SavedData together in backups; independently rolling either back invalidates the
at-most-once fence. Filesystems must honor exclusive creation and force operations.
Old rewards already lost by the previous consume-before-deliver fallback cannot
be reconstructed by this migration.

With Economy present, World's only payment path remains `DungeonRewards.grant`.
Its per-player receipt is the existing `dungeon:cycle[:room:index]`, preserving
old receipts. World never interprets a rejection/bridge error as absence and never
mints currency independently. Economy's Nations compatibility checks, Auth checks,
wallet journal, capacity checks and receipt handling remain authoritative. A
successful grant followed by a World crash retries that same receipt. Full
compatibility still requires real multiplayer and crash-injection testing.

## Administrative and terrain safety

Every confirmation requires its nonce, including typed commands. Clickable
confirmations remain available; tokens expire after 60 seconds, are bound to the
actor, and are replaced by newer confirmations. Destructive dungeon callbacks
also reject a changed dungeon object/cycle. Short ID resolution rejects ambiguity.
`force` retains claim, overlap, structure and possible player-build checks; it only
relaxes terrain suitability and spacing. Natural-looking player-placed stone or
dirt cannot be distinguished from terrain by vanilla block state: administrators
must inspect explicit generation sites even when validation passes.

Removal unregisters protection and encounters and retains pending claims. Physical
blocks remain. No terrain restoration is attempted, and confirmations/help warn
about that limitation. Introducing trustworthy snapshots would also require
crash-safe placement journalling and conflict-aware restoration; this pass does
not add a partial restoration algorithm. `recover` rebuilds the registered layout
and can replace creative operator edits within it; inspect before confirming.
Existing protection, creative operator access, permanent PvP hooks, alternate
routes and mountain escape ladders are retained. Geometry tests cover all three
themes; live combat, adjacent terrain access and PvP still need playtesting.
