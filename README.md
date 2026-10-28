# AODLed

A notification light for the Always-On Display: a ring, dot, edge or cutout outline in the colour of the newest
pending notification, drawn over a black screen while the phone is locked.

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Settings screen" width="270">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Editor placing the light" width="270">
</p>

## Features

- **Four styles:** a ring around the camera hole, a small dot, a glow along the screen edges, or the camera
  cutout's own outline.
- **Animations:** off (a still light, kindest to the battery), a slow pulse, or motion: a swirl around the ring,
  a line tracing the cutout, a comet along the edges.
- **Colours:** each app's own colour, taken from its notification or its icon, or one fixed colour, from the palette or your own.
- **Light off:** when you dismiss the notification, or as soon as you unlock the phone. A new message brings it back.
- **Only what matters:** ongoing, silent and Do Not Disturb-muted notifications don't light it.
- **Private Space:** notifications from the Private Space light it in the right app colour.
- **Place it yourself:** drag and pinch the light in a full-screen editor that uses the same drawing code as the
  always-on display.
- **Burn-in care:** the light moves by a few pixels each time the screen turns off.
- **Status at a glance:** the settings screen shows which permission is missing and takes you to fix it.
- **Private by design:** no internet permission, no tracking; notification text is never read or stored.

AODLed is heavily inspired by [HoleyLight](https://github.com/Chainfire/HoleyLight) by Chainfire. HoleyLight was last
updated about three years ago and has stopped working, so AODLed brings the same idea back.

## Scope

Like HoleyLight, I'm building AODLed for myself: an always-on display that is completely black, with nothing on it
but a notification light. It's deliberately bare, and I want to keep it that way.

Pull requests are welcome, especially fixes and support for other phones. New features need a good reason to exist,
so please open an issue to talk about one before you build it.

## Tested on

- Pixel 10a, GrapheneOS (Android 17)

AODLed needs Android 12 or newer. It hasn't been tried on other phones yet; if you do, please open an issue with
your phone, Android version and whether the light works.

## Install

- **F-Droid:** once it's listed.
- **Obtainium:** add `https://github.com/7Ds7/AODLed`. Obtainium installs the APK from each release and keeps it
  updated.
- **By hand:** download `AODLed-<version>-release.apk` from the releases page and open it, or use
  `adb install --user 0 AODLed-<version>-release.apk`.

Every channel ships the same APK, signed with the same key, so you can switch between them without reinstalling.
Signing certificate SHA-256: `9b:c1:75:87:5c:de:a6:72:a4:30:d5:69:ec:7b:f3:74:64:ed:4a:95:f2:28:03:eb:fb:69:88:96:bf:3c:18:c0`

## Setup

Open AODLed: the Status card lists what's still off, and tapping a row opens the right settings page.

| Permission | Why |
|---|---|
| Notification access | To see which apps have pending notifications and their colours. Notification text is never read or stored. |
| Accessibility service | Lets AODLed draw over the always-on display. It doesn't read the screen. |
| Battery unrestricted | So Android doesn't stop the light while the phone sleeps. |
| Always-on display | The system setting itself; without it the screen is simply off when locked. |
| Notifications | Only for the test notification; asked the first time you tap Test (Android 13+). |

If the Accessibility or Notification access toggle is greyed out, Android is restricting a sideloaded app: open
App info → ⋮ → *Allow restricted settings*, then try again.

## Building

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/AODLed-<version>-debug.apk
./gradlew testDebugUnitTest
```

Needs JDK 17 or newer and the Android SDK; releases are built with JDK 21. To sign releases, create
`keystore.properties` in the repository root (git ignores it):

```
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without it, `./gradlew assembleRelease` builds an unsigned APK, which is what F-Droid's build server does before
checking it against the signed release.

## License

Copyright (C) 2026 BinaryUnit

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see
<https://www.gnu.org/licenses/>.

SPDX-License-Identifier: GPL-3.0-or-later
