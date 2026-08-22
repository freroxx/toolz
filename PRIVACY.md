Privacy Policy for Toolz

Toolz is local-first. No analytics, no ads, no tracking by default.

**What stays on device:** Wi-Fi scans, tweak journal, speed history (Room, 90-day retention, user-clearable), clipboard history, notes, passwords (SQLCipher). Nothing leaves the phone unless you tap a network egress action below.

**Network egress — only when you tap:**
- Public IP: `https://api.ipify.org?format=json` fallback `https://ipapi.co/json/` (fetch on Overview → Public IP → Refresh)
- Speed test mirrors: `https://speed.cloudflare.com/__down?bytes=…`, `https://proof.ovh.net/files/10Mb.dat`, `https://speed.hetzner.de/100MB.bin`, upload to `https://speed.cloudflare.com/__up` (Diagnostics → Speed Test → Run)
- DNS probes: DoH JSON to the provider’s `dohUrl` (e.g. `https://cloudflare-dns.com/dns-query`, `https://dns.google/resolve`, `https://dns.quad9.net/dns-query`, `https://dns.adguard-dns.com/dns-query`, etc. — see `DnsProviderLibrary.kt:29`) + TCP :53 fallback + optional DoT :853 TLS handshake (DNS → Benchmark)
- Traceroute: local `ping -t TTL` only; optional online ASN/name enrichment via `https://ipinfo.io/<ip>/json` **only if** user enables ASN toggle (off by default)
- Whisper messaging (if used): Supabase `*.supabase.co` + storage for encrypted images

All network features degrade gracefully without Shizuku (deep-link to `Settings.ACTION_WIFI_SETTINGS` / `Settings.ACTION_PRIVATE_DNS_SETTINGS`).

Toolz does not require accounts. Toolz does not sell data.

For any questions, contact: frerox.toolz@gmail.com
