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

package com.frerox.toolz.data.network

/**
 * SINGLE source of truth for DNS resolver metadata.
 *
 * Previously three lists drifted apart (DnsEngine inline list, the Wi-Fi tweaks
 * benchmark Triple list, and the PowerSuite copy) with conflicting hostnames
 * (e.g. AdGuard's deprecated dns.adguard.com vs dns.adguard-dns.com).
 */
object DnsProviderLibrary {

    val providers: List<DnsProvider> = listOf(
        DnsProvider(
            id = "cloudflare",
            name = "Cloudflare",
            addresses = listOf("1.1.1.1", "1.0.0.1"),
            hostname = "1dot1dot1dot1.cloudflare-dns.com",
            dohUrl = "https://cloudflare-dns.com/dns-query",
            categories = setOf(DnsCategory.SPEED, DnsCategory.PRIVACY),
            description = "Low-latency global resolver with strong uptime.",
            badge = "Fast path"
        ),
        DnsProvider(
            id = "google",
            name = "Google Public DNS",
            addresses = listOf("8.8.8.8", "8.8.4.4"),
            hostname = "dns.google",
            dohUrl = "https://dns.google/resolve",
            categories = setOf(DnsCategory.SPEED, DnsCategory.SECURITY),
            description = "Reliable anycast DNS with broad regional coverage.",
            badge = "Balanced"
        ),
        DnsProvider(
            id = "quad9",
            name = "Quad9",
            addresses = listOf("9.9.9.9", "149.112.112.112"),
            hostname = "dns.quad9.net",
            dohUrl = "https://dns.quad9.net/dns-query",
            categories = setOf(DnsCategory.SECURITY, DnsCategory.PRIVACY),
            description = "Threat-filtering resolver tuned for malicious domain blocking.",
            badge = "Shielded"
        ),
        DnsProvider(
            id = "adguard",
            name = "AdGuard",
            addresses = listOf("94.140.14.14", "94.140.15.15"),
            hostname = "dns.adguard-dns.com",
            dohUrl = "https://dns.adguard-dns.com/dns-query",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.SECURITY),
            description = "Privacy-forward resolver with filtering variants.",
            badge = "Privacy"
        ),
        DnsProvider(
            id = "nextdns",
            name = "NextDNS",
            addresses = listOf("45.90.28.0", "45.90.30.0"),
            hostname = "dns.nextdns.io",
            dohUrl = "https://dns.nextdns.io",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.SECURITY),
            description = "Customizable filtering and analytics platform.",
            badge = "Custom"
        ),
        DnsProvider(
            id = "mullvad",
            name = "Mullvad DNS",
            addresses = listOf("194.242.2.2"),
            hostname = "dns.mullvad.net",
            dohUrl = "https://dns.mullvad.net/dns-query",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.SECURITY),
            description = "Minimal-logging resolver from the Mullvad privacy stack.",
            badge = "No logs"
        ),
        DnsProvider(
            id = "opendns",
            name = "OpenDNS",
            addresses = listOf("208.67.222.222", "208.67.220.220"),
            hostname = "doh.opendns.com",
            dohUrl = "https://doh.opendns.com/dns-query",
            categories = setOf(DnsCategory.SECURITY, DnsCategory.FAMILY),
            description = "Cisco-backed resolver with family-safe variants.",
            badge = "Family"
        ),
        DnsProvider(
            id = "cleanbrowsing",
            name = "CleanBrowsing",
            addresses = listOf("185.228.168.168", "185.228.169.168"),
            hostname = "security-filter-dns.cleanbrowsing.org",
            dohUrl = "https://doh.cleanbrowsing.org/doh/security-filter/",
            categories = setOf(DnsCategory.FAMILY, DnsCategory.SECURITY),
            description = "Family and security filtering built into the resolver.",
            badge = "Guardrail"
        ),
        DnsProvider(
            id = "controld",
            name = "Control D",
            addresses = listOf("76.76.2.0", "76.76.10.0"),
            hostname = "freedns.controld.com",
            dohUrl = "https://freedns.controld.com/p2",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.FAMILY),
            description = "Flexible profiles for privacy, ad blocking, and family controls.",
            badge = "Flexible"
        ),
        DnsProvider(
            id = "comodo",
            name = "Comodo Secure DNS",
            addresses = listOf("8.26.56.26", "8.20.247.20"),
            categories = setOf(DnsCategory.SECURITY),
            protocols = setOf(DnsProtocol.DOH),
            description = "Legacy malware-focused filtering resolver.",
            badge = "Security"
        ),
        DnsProvider(
            id = "cloudflare_family",
            name = "Cloudflare Family",
            addresses = listOf("1.1.1.3", "1.0.0.3"),
            hostname = "family.cloudflare-dns.com",
            dohUrl = "https://family.cloudflare-dns.com/dns-query",
            categories = setOf(DnsCategory.FAMILY, DnsCategory.SECURITY),
            description = "Blocks malware + adult content.",
            badge = "Family"
        ),
        DnsProvider(
            id = "adguard_family",
            name = "AdGuard Family",
            addresses = listOf("94.140.14.15", "94.140.15.15"),
            hostname = "family.adguard-dns.com",
            dohUrl = "https://family.adguard-dns.com/dns-query",
            categories = setOf(DnsCategory.FAMILY, DnsCategory.SECURITY),
            description = "Ad/tracker blocking + adult-content filter.",
            badge = "Family"
        ),
        DnsProvider(
            id = "quad9_unsecured",
            name = "Quad9 (unfiltered)",
            addresses = listOf("9.9.9.10", "149.112.112.10"),
            hostname = "dns10.quad9.net",
            dohUrl = "https://dns10.quad9.net/dns-query",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.SPEED),
            description = "No blocking — pure privacy resolver.",
            badge = "Unfiltered"
        ),
        DnsProvider(
            id = "opendns_familyshield",
            name = "OpenDNS FamilyShield",
            addresses = listOf("208.67.222.123", "208.67.220.123"),
            hostname = "doh.familyshield.opendns.com",
            dohUrl = "https://doh.familyshield.opendns.com/dns-query",
            categories = setOf(DnsCategory.FAMILY),
            description = "Pre-configured adult-content blocking.",
            badge = "Family"
        ),
        DnsProvider(
            id = "dns_sb",
            name = "DNS.SB",
            addresses = listOf("185.222.222.222", "45.11.45.11"),
            hostname = "dot.sb",
            dohUrl = "https://doh.sb/dns-query",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.SPEED),
            description = "Non-profit, no logging, no filtering.",
            badge = "No logs"
        ),
        DnsProvider(
            id = "yandex",
            name = "Yandex DNS",
            addresses = listOf("77.88.8.8", "77.88.8.1"),
            hostname = "common.dot.dns.yandex.net",
            dohUrl = "https://common.dot.dns.yandex.net/dns-query",
            categories = setOf(DnsCategory.SPEED),
            description = "Fast resolver with strong EU/Asia presence.",
            badge = "Global"
        )
    )

    /** Primary address per provider, used by the legacy benchmark selection sheet. */
    val benchmarkTriples: List<Triple<String, String, String>> =
        providers.map { Triple(it.id, it.name, it.addresses.first()) }

    fun byId(id: String): DnsProvider? = providers.firstOrNull { it.id == id }
}
