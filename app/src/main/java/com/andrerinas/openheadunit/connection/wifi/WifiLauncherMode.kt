package com.andrerinas.openheadunit.connection.wifi

import com.andrerinas.openheadunit.connection.wifi.modes.WifiLauncherAuto
import com.andrerinas.openheadunit.connection.wifi.modes.WifiLauncherHelper
import com.andrerinas.openheadunit.connection.wifi.modes.WifiLauncherManual
import com.andrerinas.openheadunit.connection.wifi.modes.WifiLauncherNative

enum class WifiLauncherMode(
    val id: Int,
    val factory: (WifiLauncherManager) -> WifiLauncher) {

    MANUAL(0, ::WifiLauncherManual),
    AUTO(1, ::WifiLauncherAuto),
    HELPER(2, ::WifiLauncherHelper),
    NATIVE(3, ::WifiLauncherNative);

    companion object {

        // MapLink defaults to Headunit Server discovery. This is the most compatible
        // wireless path for an old Android 6 receiver such as the Samsung J2 Prime.
        // Native AA remains available in Settings for hardware that supports it well.
        val DEFAULT: WifiLauncherMode = AUTO


        fun byIdOrDefault(id: Int): WifiLauncherMode {
            for (mode in WifiLauncherMode.entries) {
                if (mode.id == id)
                    return mode
            }

            return DEFAULT
        }
    }
}
