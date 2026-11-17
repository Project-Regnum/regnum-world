# Growth integration contract v1

`RegnumWorldApi.GROWTH_API_VERSION == 1` describes the additive contract below.
Existing controller registration, result enum and regional multiplier methods keep
their signatures. All world queries, callbacks and replays run on the server thread.

* `evaluateCropGrowth(ServerLevel, BlockPos, BlockState)` returns a
  `CropGrowthPolicy(version, blocked, multiplier)`. This is a policy query, with
  no random rolls, controller callbacks or crop mutation. `blocked` describes the
  configured underground/sky restriction; blocked policies return multiplier zero.
  A nonblocked multiplier can also be zero due to biome/crop/altitude rules.
* `registerGrowthController(ExternalGrowthController)` registers in order;
  unregister during teardown with the same instance. `getControllers()` returns an
  immutable snapshot; registration methods are the supported mutation path.
* World checks the underground restriction before delegating supported plant ticks.
  Controllers receive the computed multiplier exactly once. The first `HANDLED`
  or `CANCEL` stops dispatch and cancels vanilla growth. `PASS` promises the
  controller did not mutate growth; World proceeds to its adapter/fallback rules.
  With no controller, World uses those same rules. Null results are invalid.
* A controller applying the supplied multiplier must run bounded vanilla original
  ticks inside `replayCropTick(Runnable)` and return `HANDLED`. This scoped method
  bypasses World growth evaluation for the nested ticks and restores scope even
  when the action throws. It is legal only during a synchronous controller callback;
  it must not schedule work into a later tick. Nested unmarked dispatch returns
  `CANCEL`, preventing recursive controller invocation. External callers should
  not call `delegateCropTick` themselves; it is World's dispatch entry point.
* For plants driven entirely by another mod outside World's intercepted block
  classes, query policy once and apply it in that mod's own growth path. This API
  does not globally discover or intercept all modded plants.

Agriculture can use this public replay scope instead of intercepting internal
`GrowthHelper`. The old helper signature remains available for its existing optional
mixin during migration. Agriculture's repository was not changed.

World has **no authoritative season or calendar system** in the inspected source.
There is no season API and no invented day-to-season mapping. A seasonal controller
must obtain season data from its actual owner, then combine that factor with the
World policy once. Adding a real seasonal source would require a separate contract.
