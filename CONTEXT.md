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

## Brew

**Brew** — one pour, from the moment the timer starts to the moment it stops.

**Recipe** — a named, ordered list of Stages. Stored as JSON in `SharedPreferences`.

**Stage** — one step of a Recipe: a name, a time window (`startSec`..`endSec`), a cumulative
`targetWeight` in grams, and a note. Targets are cumulative, not per-stage: a Stage's own
contribution is its target minus the previous Stage's.

**Brew Session** — not yet a module. The rules that turn weight samples into a Brew: auto-start
above 0.5 g, auto-stop after 1.5 s below 0.2 g, smoothed flow rate, and Stage advance. Currently
scattered across `MainActivity` as private fields; extracting it is the next planned change.
