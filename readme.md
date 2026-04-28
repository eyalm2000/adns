# ADNS

![Version](https://img.shields.io/github/v/release/eyalm2000/adns?label=Version)
![IzzyOnDroid downloads](https://img.shields.io/badge/dynamic/json?url=https://dlstats.izzyondroid.org/iod-stats-collector/stats/basic/yearly/rolling.json&query=$.[%27com.eyalm.adns%27]&label=IzzyOnDroid%20downloads)
![GitHub downloads](https://img.shields.io/github/downloads/eyalm2000/adns/total?label=GitHub%20downloads&color=blue)

ADNS is a lightweight DNS-based ad blocker for Android. No VPN, no background services, no battery drain, no hassle.

Download it from [GitHub Releases](https://github.com/eyalm2000/adns/releases) or [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/com.eyalm.adns).

## Features

Android makes DNS controls hard to find and slow to toggle. ADNS makes it fast and accessible.

- Toggle DNS on/off with a single tap
- Use your own DNS server
- Quick Settings tile for instant access
- State notification for at-a-glance status
  
Beautifully crafted with Material You and Jetpack Compose.

## Activation

ADNS writes to global DNS settings, which requires access for `WRITE_SECURE_SETTINGS`.

You can grant access using:

- [Shizuku](https://github.com/RikkaApps/Shizuku) (recommended)
- ADB shell

<br>
<br>
<p align="center">
  <img src="assets/homescreen.png" alt="ADNS main screen" width="33%">
  <img src="assets/settings.png" alt="ADNS settings screen" width="33%">
</p>
