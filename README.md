# AODLed

A notification light for the Always-On Display: a ring, dot, edge or cutout outline in the colour of the newest
pending notification, drawn over a black screen while the phone is locked.

## Features

- **Four styles:** a ring around the camera hole, a small dot, a glow along the screen edges, or the camera
  cutout's own outline.
- **Animations:** off (a still light, kindest to the battery), a slow pulse, or motion: a swirl around the ring,
  a line tracing the cutout, a comet along the edges.
- **Colours:** each app's own colour, taken from its notification or its icon, or one fixed colour from a palette.
- **Light off:** when you dismiss the notification, or as soon as you unlock the phone. A new message brings it back.
- **Only what matters:** ongoing, silent and Do Not Disturb-muted notifications don't light it.
- **Private Space:** notifications from the Private Space light it in the right app colour.
- **Place it yourself:** drag and pinch the light in a full-screen editor that uses the same drawing code as the
  always-on display.
- **Burn-in care:** the light moves by a few pixels each time the screen turns off.
- **Status at a glance:** the settings screen shows which permission is missing and takes you to fix it.
- **Private by design:** no internet permission, no tracking; notification text is never read or stored.

## Tested on

- Pixel 10a, GrapheneOS (Android 17)

AODLed needs Android 12 or newer. It hasn't been tried on other phones yet; if you do, please open an issue with
your phone, Android version and whether the light works.

AODLed is heavily inspired by [HoleyLight](https://github.com/Chainfire/HoleyLight) by Chainfire. HoleyLight was last
updated about three years ago and has stopped working, so AODLed brings the same idea back.

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
