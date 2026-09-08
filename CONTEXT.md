# Domain model

Terms used consistently across the Android app, the firmware, and these docs. If a name here and a
name in the code disagree, one of them is a bug.

## Device

**Scale** — the physical device: an nRF52840 board with a NAU7802 load-cell ADC, an SSD1306 OLED,
and a BLE radio. Firmware lives in `scales_bt/scales_bt.ino`. It owns weight measurement,
filtering, calibration storage, and its own display. It does not own the brew.

**Calibration** — the pair (`zero`, `factor`) the Scale persists to `/scale_cal.bin`: a raw ADC
count for the empty pan and a counts-per-gram scale factor. `factor` is normally negative, because
of the load cell bridge polarity. Distinct from **Tare**, which shifts the zero for one brew only
and is never persisted.

## Wire

**ScaleCommand** — a message the app sends to the Scale, as a value rather than a call
(`ScaleCommand.Tare`, `SetStage`, …). Callers say *what* they mean; only `ScaleProtocol` knows how
it is spelled.

**ScaleEvent** — something the Scale told us, decoded from a notification or read
(`Weight`, `Battery`, `Calibration`).

**ScaleProtocol** — the specification of the wire format, in
`android-app/.../scale/ScaleProtocol.kt`. It is the single written statement of the format; the
firmware's `handleControlCommand` and `notify` calls are the second implementation of it. Its unit
tests are the contract between the two.

**ScaleLink** — the seam between the brew and the radio: `connect`/`disconnect`/`send`, plus a
`StateFlow<LinkState>` and a `Flow<ScaleEvent>`. Two adapters satisfy it — `GattScaleLink` over
the Android Bluetooth stack, and `FakeScaleLink` replaying a scripted pour for tests and Compose
previews.

**LinkState** — how the connection is doing: `Idle`, `Scanning`, `Connecting`, `Ready`, or
`Failed(reason)`. Carries no display strings; the UI decides what these are called.

**RecipeStore** — where Recipes are kept. A load never returns nothing: an empty or unreadable
store yields `DefaultRecipes`, flagged so the UI can say so.

## Brew

**Brew** — one pour, from the moment the timer starts to the moment it stops.

**Recipe** — a named, ordered list of Stages. Stored as JSON in `SharedPreferences`.

**Stage** — one step of a Recipe: a name, a time window (`startSec`..`endSec`), a cumulative
`targetWeight` in grams, and a note. Targets are cumulative, not per-stage: a Stage's own
contribution is its target minus the previous Stage's.

**Brew Session** — the rules that turn weight samples into a Brew: auto-start above 0.5 g,
auto-stop after 1.5 s below 0.2 g, smoothed flow rate, Stage advance, and the chart trace. Lives in
`android-app/.../brew/` as a pure reducer: `BrewSession.reduce(state, input)` returns a new
`BrewState` and any `ScaleCommand`s the transition implies. It reads no clock and performs no side
effects — time arrives as an `atMs` argument, and commands are returned rather than sent, so the
whole ruleset is exercisable on the JVM.

**BrewInput** — something that happens to a Brew Session: a weight `Sample`, a timer control, a
Stage advance, a Recipe change, a disconnect.
