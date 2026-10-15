# Switch 1 Joy-Cons on Android

What the kernel and Android do with original Joy-Cons (`057e:2006` left, `057e:2007` right), measured for
[switch1-support-plan.md](switch1-support-plan.md). Raw captures: `tools/probe-out/tb305fu-a15/`
(not committed; made by `tools/switch1_probe.sh`).

## Measured: Lenovo TB305FU, Android 15, kernel 6.6.118, 2026-10-15

| Fact | Result |
|---|---|
| `hid-nintendo` | `CONFIG_HID_NINTENDO=y`; `CONFIG_NINTENDO_FF` unset (no rumble) |
| Pairing | Plain Bluetooth settings; bus `0x0005` |
| Devices per Joy-Con | **Two**: the pad, and `… Joy-Con IMU` |
| Combined L+R device | **None.** Four devices for a pair; each pad is its own `GAMEPAD \| JOYSTICK` with `Generic.kl` |
| Identity key | `UniqueId` is the Bluetooth MAC, shared by a Joy-Con's pad and IMU; use it to join them |
| Node access | `/dev/input/event*` is `root:input 0660`; shell uid is in group `input`, so a Shizuku process can read it |
| `/dev/hidraw*` | `system:system 0660`, not readable by shell |
| SELinux | Enforcing; `getevent` as shell works |
| Battery | `power_supply` `nintendo_switch_controller_battery_<hid id>`; `capacity` file not readable as shell |
| Player LEDs | Four green + one blue (`…:player-1`…`player-5`) under `leds` |
| `EVIOCGRAB` as shell uid | Works. Grabbed left Joy-Con: a second reader saw 0 events, the grabbing process saw all; ungrabbed: 1,135 and 1,511 lines (TB305FU, 2026-10-15) |
| Node identity | Shell can't read sysfs, but `EVIOCGID`/`EVIOCGUNIQ` on an opened node give vendor, product and MAC |
| Android descriptor | `sha1(":%04x:%04x:uniqueId:%s")` of vendor, product and the MAC, matching `InputDevice.getDescriptor()`; how a node is matched to its `InputDevice` |
| Idle IMU | About 1.5 kHz of events per Joy-Con; filter by device before decoding |

### Left Joy-Con, node `event5`

| Input | Evdev code | Input | Evdev code |
|---|---|---|---|
| Stick | `ABS_X` / `ABS_Y` | Stick click | `BTN_THUMBL` |
| D-pad | `BTN_DPAD_UP/DOWN/LEFT/RIGHT` — **buttons, not a hat** | `−` | `BTN_SELECT` |
| Capture | `BTN_Z` | L / ZL | `BTN_TL` / `BTN_TL2` |
| SL / SR | `BTN_TR` / `BTN_TR2` | | |

### Right Joy-Con, node `event7`

| Input | Evdev code | Input | Evdev code |
|---|---|---|---|
| Stick | `ABS_RX` / `ABS_RY` | Stick click | `BTN_THUMBR` |
| B / A / Y / X | `BTN_GAMEPAD` (South) / `BTN_EAST` / `BTN_WEST` / `BTN_NORTH` (confirmed) | `+` | `BTN_START` |
| Home | `BTN_MODE` | R / ZR | `BTN_TR` / `BTN_TR2` |
| SL / SR | `BTN_TL` / `BTN_TL2` | | |

Stick range ±32767 (driver-calibrated), reaching ≈ ±31 000 in the capture. **Up is negative Y and
right is positive X**, measured on the right stick; the left stick's up was not captured, and is
expected to match. A sideways Joy-Con's SL/SR sit on opposite shoulder codes per side: the left's are
`TR`/`TR2`, the right's `TL`/`TL2`.

### IMU nodes (`event6` left, `event8` right)

`ABS_X/Y/Z` accelerometer, resolution 4096 per g; `ABS_RX/RY/RZ` gyro, range ±32 767 000, resolution
14247. `MSC_TIMESTAMP` present, `INPUT_PROP_ACCELEROMETER`. Resting values of both Joy-Cons are
≈ 1 g on `ABS_Z`.

## Consequences for the plan

- **Phase 0 branch: two devices.** Android 15 here does not combine a pair, so the app merges them.
- `hid-nintendo` is present: no `hidraw` fallback is needed on this device.
- The D-pad is buttons, so `EvdevStateDecoder` maps `BTN_DPAD_*` directly to `Up/Down/Left/Right`.
- Joy-Con 1 SL/SR sit on the shoulder codes, so the Joy-Con 2 sideways handling needs the code map
  before it can be reused.
- Android already exposes the raw Joy-Cons as gamepads, so the exclusive grab stays necessary.

## Unconfirmed

1. Left stick up direction (expected negative Y).
2. The grab after the app is killed mid-grab (helper should exit on stdin EOF).
3. Reconnect after power-off by button press.
4. PXPlay with the merged pad. A grabbed Joy-Con is still enumerated by Android, only silent, which may
   confuse a picker.
5. Other devices: AYN Thor, Retroid Pocket Nova, and an Android 11 device.
