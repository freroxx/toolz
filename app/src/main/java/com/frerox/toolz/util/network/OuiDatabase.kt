/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.util.network

/**
 * Curated offline MAC→vendor lookup (IEEE OUI subset).
 * Covers the manufacturers that own most home-network share so device cards read
 * "Apple", "Raspberry Pi Trading" instead of "Unknown" — no network egress required.
 */
object OuiDatabase {

    private val prefixes: Map<String, String> = mapOf(
        // Apple
        "00:03:93" to "Apple", "00:05:02" to "Apple", "00:0A:27" to "Apple",
        "00:0D:93" to "Apple", "00:10:FA" to "Apple", "00:14:51" to "Apple",
        "00:16:CB" to "Apple", "00:17:F2" to "Apple", "00:19:E3" to "Apple",
        "00:1B:63" to "Apple", "00:1E:52" to "Apple", "00:1F:5B" to "Apple",
        "00:21:E9" to "Apple", "00:22:41" to "Apple", "00:23:12" to "Apple",
        "00:23:DF" to "Apple", "00:25:00" to "Apple", "00:25:4B" to "Apple",
        "00:26:08" to "Apple", "00:26:4A" to "Apple", "00:26:B0" to "Apple",
        "04:0C:CE" to "Apple", "08:66:98" to "Apple", "AC:87:A3" to "Apple",
        "A4:D1:8C" to "Apple", "D0:E1:40" to "Apple", "F0:18:98" to "Apple",

        // Samsung
        "00:07:AB" to "Samsung", "00:12:47" to "Samsung", "00:16:32" to "Samsung",
        "00:1B:98" to "Samsung", "00:1D:25" to "Samsung", "00:1E:E2" to "Samsung",
        "00:21:19" to "Samsung", "00:23:99" to "Samsung", "00:24:54" to "Samsung",
        "00:26:37" to "Samsung", "08:21:EF" to "Samsung", "18:22:36" to "Samsung",
        "24:DB:ED" to "Samsung", "30:96:FB" to "Samsung", "38:01:67" to "Samsung",
        "40:0E:85" to "Samsung", "50:CC:F8" to "Samsung", "60:AF:6D" to "Samsung",
        "78:25:AD" to "Samsung", "84:38:35" to "Samsung", "8C:77:12" to "Samsung",
        "A8:06:00" to "Samsung", "C4:57:6E" to "Samsung", "E8:50:8B" to "Samsung",
        "FC:F1:52" to "Samsung",

        // Google / Nest
        "00:09:B0" to "Google", "00:0D:4B" to "Google", "00:11:85" to "Google",
        "00:12:D9" to "Google", "00:18:71" to "Google", "00:1A:11" to "Google",
        "00:1F:5F" to "Google", "54:60:09" to "Google", "64:BC:0C" to "Google",
        "6C:AD:F8" to "Google", "74:DA:38" to "Google", "88:58:39" to "Google",
        "94:EB:2C" to "Google", "A4:77:33" to "Google", "B4:E1:C4" to "Google",
        "F4:F5:D8" to "Google", "FC:A1:3E" to "Google",

        // Routers & networking
        "00:1A:2B" to "Ayecom", "14:CC:20" to "TP-Link", "18:D6:C7" to "TP-Link",
        "20:DC:E6" to "TP-Link", "30:B5:C2" to "TP-Link", "50:C7:BF" to "TP-Link",
        "60:32:B1" to "TP-Link", "64:66:B3" to "TP-Link", "6C:B7:49" to "Netgear",
        "74:44:01" to "Netgear", "9C:3D:CF" to "Netgear", "A0:40:A0" to "Netgear",
        "B0:48:7A" to "TP-Link", "C0:25:E9" to "TP-Link", "D4:6E:5C" to "D-Link",
        "E4:6F:13" to "TP-Link", "FC:EC:DA" to "Ubiquiti", "24:A4:3C" to "Ubiquiti",
        "68:D7:9A" to "Ubiquiti", "78:8A:20" to "Ubiquiti", "F0:9F:C2" to "Ubiquiti",
        "00:0C:29" to "VMware", "00:1B:21" to "Intel", "00:1C:BF" to "Amazon",
        "00:23:CD" to "TP-Link", "00:25:9C" to "Cisco-Linksys", "00:26:5A" to "D-Link",

        // Single-board / dev boards / IoT
        "B8:27:EB" to "Raspberry Pi Trading", "DC:A6:32" to "Raspberry Pi Trading",
        "E4:5F:01" to "Raspberry Pi Trading", "28:CD:C1" to "Raspberry Pi Trading",
        "D8:3A:DD" to "Raspberry Pi Trading", "2C:CF:67" to "Raspberry Pi Trading",
        "24:0A:C4" to "Espressif", "5C:CF:7F" to "Espressif", "18:FE:34" to "Espressif",
        "30:AE:A4" to "Espressif", "3C:71:BF" to "Espressif", "A4:CF:12" to "Espressif",
        "BC:DD:C2" to "Espressif", "EC:FA:BC" to "Espressif", "84:F3:EB" to "Espressif",
        "00:1E:C0" to "Sonos", "34:7E:5C" to "Sonos", "48:A6:B8" to "Sonos",
        "54:2A:1B" to "Sonos", "78:28:CA" to "Sonos", "94:9F:3E" to "Sonos",
        "B8:E9:37" to "Sonos", "00:04:20" to "Slim Devices", "00:17:88" to "Signify (Philips Hue)",
        "00:1B:52" to "Signify (Philips Hue)", "00:11:32" to "Synology", "00:90:0B" to "Lorex",
        "00:18:E7" to "Parrot", "00:1D:BA" to "GetWell", "00:26:BB" to "Apple",

        // PCs / NIC vendors
        "3C:97:0E" to "Wistron", "00:1F:16" to "Quanta", "70:5A:B6" to "AzureWave",
        "00:15:5D" to "Microsoft (Hyper-V)", "7C:1E:52" to "ASUS", "AC:22:0B" to "ASUS",
        "40:B0:76" to "ASUS", "00:23:54" to "Duons", "00:24:8C" to "Whirlpool",
        "0C:9D:9E" to "AzureWave", "20:DC:E6" to "TP-Link", "B8:53:AC" to "HTC",
        "00:50:56" to "VMware", "00:0F:EA" to "SEH", "00:80:92" to "AVM GmbH",
        "C8:0E:77" to "Western Digital", "00:1D:73" to "Western Digital",
        "00:1C:C4" to "HP", "3C:D9:2B" to "HP", "F4:39:09" to "HP",
        "D8:CB:8A" to "Micro-Star", "40:8D:5C" to "GIGA-BYTE", "50:E5:49" to "GIGA-BYTE"
    )

    /** Normalizes any common MAC format to AA:BB:CC:DD:EE:FF uppercase, or null. */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val hex = raw.filter { it.isLetterOrDigit() }
        if (hex.length != 12 || hex.any { it.lowercaseChar() !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) return null
        return hex.chunked(2).joinToString(":").uppercase()
    }

    fun vendor(mac: String?): String {
        val norm = normalize(mac) ?: return "Unknown"
        val prefix = norm.substring(0, 8)
        return prefixes[prefix]
            ?: prefixes.entries.firstOrNull { prefix.startsWith(it.key.substring(0, 8)) }?.value.takeIf { false } ?: "Unknown"
    }
}
