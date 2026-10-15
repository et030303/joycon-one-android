# Switch 1 controller support — phased plan

Adds original Switch 1 Joy-Cons (`057e:2006` left, `057e:2007` right) as a second controller source.
They pair through Android's Bluetooth settings as ordinary HID, so none of the BLE/GATT code applies.
Scope: **Joy-Cons only, Android 11+ (API 30)**; the Switch 1 Pro Controller is deferred. The app
stays minSdk 24 for Joy-Con 2; the Switch 1 source is simply disabled below API 30.
Everything downstream of the controller list — assignment, button mapping, UHID virtual gamepad, DSU,
emulator setup — is reused unchanged. Source: [Joycon_Merger_PRD_and_Prompt.md](../../docs/Joycon_Merger_PRD_and_Prompt.md).

## Where this plan departs from the PRD

| PRD says | Plan | Why |
|---|---|---|
| Write to `/dev/uinput` | Reuse the UHID relay ([virtual-gamepad.md](virtual-gamepad.md#uhid)) | Already works through Shizuku, one device per player, with a report mapper, Android-key routing and emulator setup built on it. uinput would duplicate all of it. |
| Intercept `KeyEvent`/`MotionEvent` "globally" | Read `/dev/input/event*` through a shell-uid native reader | Android delivers `InputEvent`s only to the focused window. An app in the background, which is the whole use case, gets nothing. `InputManager` is used for discovery only. |
| Merge two devices into one pad | Reuse player assignment; merge in `PlayerStateResolver` | L+R combo detection and "pair" players already exist. |
| Emulate a DS4 (`054c:05c4`) | One merged virtual device is the hard requirement; its identity (generic, Xbox 360 or DS4) is settled by an early PXPlay test | `hid-sony` would claim a DS4 ID and expect DS4 reports and feature reports. Unproven that PXPlay needs it. |
| — | Exclusively grab the physical devices (`EVIOCGRAB`) | Otherwise every game sees the two raw Joy-Cons *and* the merged pad. |

## Architecture fit

```
Joy-Con L/R ─BT HID─→ kernel (hid-nintendo?) ─→ /dev/input/eventN
                                                      │  EVIOCGRAB + read (shell uid via Shizuku)
                                                      ▼
                                            evdev_reader (native)  ─stdout─→  EvdevReader (Kotlin)
                                                                                    │ JoyconInput
                       Joycon2Manager (BLE) ──┐                                     ▼
                                              ├─→ CompositeControllerRepository ─→ SessionCoordinator ─→ (existing pipeline)
                       HidControllerSource ───┘
```

- Both sources implement `ControllerRepository`; the composite concatenates `controllers` and
  routes `disconnect`, `setPlayerLed` and `setControllerType` by address. Switch 1 addresses are
  `evdev:<bluetooth MAC from EVIOCGUNIQ>`.
- Code lives in `:feature:connection:data` under a `hid` package, with pure parsing/decoding in
  `:feature:connection:domain`. Features may not depend on each other, so no new feature module.
- Shizuku is required for input here, unlike Joy-Con 2 where only the outputs need it.

---

## Phase 0 — Feasibility spike (no app code)

**Unverified assumption: Android 11+ already presents a left + right Joy-Con pair as one unit.**
Upstream `hid-nintendo` exposes each Joy-Con as its own input device, so this must be measured, not
assumed. Step 8 below decides which branch the plan takes:

| Finding | Consequence |
|---|---|
| OS exposes L and R as two devices (expected) | Plan as written: the app merges them. |
| OS exposes one combined device | Read that single node; assignment and merging drop out, and the app only has to re-publish it as one pad PXPlay accepts. If PXPlay already sees it, there may be nothing to build. |

**The plan hangs on what the device's kernel does with a Joy-Con.** Run on each target device
(AYN Thor Android 13, Retroid Pocket Nova Android 13, one Pixel/Samsung on Android 14+, and an Android 11 device) with one left
and one right Joy-Con paired from Bluetooth settings.

1. `adb shell getevent -lp` — does a `Nintendo Switch … Joy-Con` node exist, with stick `ABS_*`
   axes and `BTN_*` codes, or only a generic HID device with a hat?
2. Press every button; record evdev code per button, including SL/SR and Capture. Left D-pad: buttons
   or hat?
3. Look for separate `… IMU` nodes and their axis ranges.
4. `adb shell dumpsys input` — which key layout Android applied; is the Joy-Con visible as a gamepad?
5. As shell uid, open an event node and issue `EVIOCGRAB`; confirm Android stops seeing the device.
6. `adb shell ls -l /dev/hidraw*` and `/sys/class/power_supply` for battery.
7. Disconnect, press a button, confirm the Joy-Con reconnects and the node reappears.
8. Count the devices Android lists for a paired L + R (`dumpsys input`, `InputDevice.getDeviceIds()`),
   and whether any is a combined one.
9. **PXPlay test.** With a Joy-Con 2 pair on the existing generic UHID pad (already working), check
   PXPlay lists and drives it. If it rejects it, Phase 7 moves up before Phase 4.

**Deliverable:** `docs/switch1.md` — a measured table (device, Android version, date) of node names,
code→`JoyconButton` map, axis ranges, IMU nodes, grab result. Raw `getevent -l` captures saved as
test fixtures.

**Go / no-go:**

| Finding | Consequence |
|---|---|
| `hid-nintendo` present, nodes as above | Proceed. |
| Generic HID only (no analog sticks) | Joy-Cons stay in simple report mode. Needs `hidraw` + subcommand `0x03` to switch to `0x30` full reports. Reader becomes a hidraw parser; Phase 3 grows. |
| Shell uid cannot read `event*` or `EVIOCGRAB` fails | Fall back to Phase 8 foreground-only mode, or require root. |

## Phase 1 — Model and decoding (pure Kotlin, JVM tests)

- **`ControllerModel` is unchanged.** A Switch 1 Joy-Con plays the same role as a Joy-Con 2, and new
  entries would leak into the type-override menu (`ControllerModel.entries`). Phase 2 tells the sources
  apart by address prefix; add a source field to `ConnectedJoycon` only if Phase 5's UI needs one.
- `EvdevCodeMap` (domain): evdev code → `JoyconButton`, from the Phase 0 table. Joy-Con 1 has no
  GL/GR/C, so those never fire.
- `EvdevStateDecoder` (domain): stream of `(deviceKey, type, code, value)` → `JoyconInput`. Sticks
  rescaled from the driver's range to 0..4095 centred on 2048, Y orientation matched to Joy-Con 2.
  The driver already applies the Joy-Con's factory calibration, so **bypass `StickCalibrator`**.
- Left D-pad to `Up/Down/Left/Right` whether the device reports buttons or a hat.
- `motionSupport = None` until Phase 6.

**Exit:** decoder tests replay the Phase 0 fixtures and assert exact `JoyconInput`s.

## Phase 2 — Discovery

- `InputDeviceWatcher` (data): `InputManager.InputDeviceListener` filtered on vendor `0x057E`,
  product `0x2006`/`0x2007`. Emits add/remove with `deviceId`, name, descriptor.
- `HidControllerSource` publishes a `ConnectedJoycon` per device (connected, `ready = false`,
  no input yet), and removes it on unplug.
- `CompositeControllerRepository` in `AppContainer`; `startScan` also runs the watcher's rescan.

**Exit:** pairing a Joy-Con in system settings makes a card appear in the app; unpairing removes it.
Works without Shizuku.

## Phase 3 — Native evdev reader

- `app/src/main/cpp/evdev_reader.c`, built next to `uhid_relay.c`: open the node, `EVIOCGRAB`,
  `epoll` on it, and write fixed-size binary records to stdout (device key, type, code, value, timestamp).
  Exit cleanly on stdin close so a dead app releases the grab.
- `EvdevReader` (data): starts one reader per Joy-Con through `PrivilegedShell`, mirroring `UhidRelay`;
  feeds `EvdevStateDecoder`.
- Map `InputDevice` ↔ `/dev/input/eventN` by descriptor/`uniq`, not by enumeration order.
- Grab only while a player is assigned and an output is on; release otherwise so the raw Joy-Con works
  as a plain gamepad when the app is idle.

**Exit:** live readout in the app shows every button and both sticks from both Joy-Cons; physical
devices vanish from other apps while grabbed. Measure reader→Kotlin latency (target < 3 ms p99).

## Phase 4 — End-to-end pipeline

- Feed `HidControllerSource` input into the composite so `SessionCoordinator` sees it identically.
- Verify L+R combo assignment, sideways `SL + SR` assignment and `SidewaysMapper` (Switch 1 rail
  buttons sit where the Joy-Con 2's do; confirm against Phase 0 codes).
- Hot-unplug mid-game: hold the player slot as `connectionLost` like BLE does; resume on reconnect
  without a re-assign.
- Both sources at once: a Joy-Con 2 pair and a Switch 1 pair on different players.

**Exit:** two Switch 1 Joy-Cons assigned to P1 appear as one `Joy-Con Virtual Gamepad 1`, and Eden and
Dolphin auto setup work with it. Hardware matrix recorded in `docs/switch1.md`.

## Phase 5 — Product surface

- Connection card: Switch 1 section reads "Pair in Bluetooth settings" with a shortcut intent to
  `ACTION_BLUETOOTH_SETTINGS`; no SYNC copy.
- Controller graphics for Joy-Con 1 (no GL/GR/C); default shell colours, since SPI colour isn't exposed
  by the driver (confirm in Phase 0).
- Strings, `DESIGN.md`/`PRODUCT.md` wording, README "What you need" and troubleshooting
  (kernel without `hid-nintendo`, grab failures).
- Battery from `power_supply` if exposed; otherwise hidden.

## Phase 6 — Motion for DSU

- Read the IMU nodes; scale to the units `JoyconInput` expects (see [dsu-motion.md](dsu-motion.md));
  a Joy-Con's IMU is a separate evdev device, so correlate by `uniq`.
- Verify axis signs and the sideways rotation against Joy-Con 2 output.
- Set `motionSupport = Full`; reuse the existing rest recalibration.

**Exit:** gyro aiming works in Dolphin/Eden from a Switch 1 Joy-Con.

## Phase 7 — PXPlay identity

PXPlay recognising the merged pad is a hard requirement. Phase 0 step 9 says whether the generic
`1234:5678` pad passes; if it does, this phase is only the final acceptance test.

1. Acceptance: one merged device, listed once in PXPlay with all buttons, both sticks, D-pad and
   triggers, while the raw Joy-Cons are grabbed and invisible.
2. If the generic pad is rejected, add an identity option: Xbox 360 (`045e:028e`) first, since a plain HID descriptor needs
   no feature-report handshake; DS4 only if required, with its full report and calibration features.
3. A second `ReportMapper`/descriptor behind a `VirtualGamepadProfile` interface; the default stays generic.

## Phase 8 — Stretch

| Item | Note |
|---|---|
| Player LEDs | `hid-nintendo` exposes LEDs in sysfs; implement `setPlayerLed`. |
| Foreground-only mode, no Shizuku | `dispatchGenericMotionEvent` while the app is in front; readout only, no virtual pad. |
| Rumble | Out of scope until a path to UHID output reports is known. |
| `hidraw` fallback | Needed only if Phase 0 finds no `hid-nintendo`. |

---

## Testing

- JVM: `EvdevCodeMap`, `EvdevStateDecoder`, address routing in the composite, all from recorded
  `getevent` fixtures.
- `./gradlew :konsist:test` after adding classes; `./gradlew test` before each phase closes.
- Hardware matrix per phase: device × Android version × Joy-Con pair, with the date.

## Risks

| Risk | Mitigation |
|---|---|
| Kernel lacks `hid-nintendo` | Phase 0 go/no-go; `hidraw` path |
| OEM SELinux blocks shell from `event*` | Phase 0 step 5; root fallback |
| `EVIOCGRAB` left held after a crash | Reader exits on stdin EOF; grab released with the fd |
| Event-to-device mapping wrong after reconnect | Key on `uniq`, not node number |
| Extra hop adds latency | Measure in Phase 3; if over budget, move merge + UHID write into one native process |
| Joy-Con 1 and 2 both present confuse side inference | Distinct `ControllerModel`s; `SideInference` only for `UNKNOWN` |

## Open questions

1. Which target devices must work (Android 11+ only)? Phase 0 decides the matrix.
2. Does Android 11+ really combine a Joy-Con pair into one device? Phase 0 step 8 answers it.
