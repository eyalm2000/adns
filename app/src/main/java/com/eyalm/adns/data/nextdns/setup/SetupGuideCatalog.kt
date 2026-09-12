package com.eyalm.adns.data.nextdns.setup

import android.content.Context
import android.net.Uri
import com.eyalm.adns.R
import com.eyalm.adns.data.Locales
import java.net.InetAddress

data class SetupGuideCatalog(
    val platforms: List<SetupGuidePlatform>,
)

data class SetupGuidePlatform(
    val id: String,
    val title: String,
    val methods: List<SetupGuideMethod>,
)

data class SetupGuideMethod(
    val id: String,
    val title: String,
    val requirements: GuideRichText? = null,
    val tags: Set<SetupGuideTag> = emptySet(),
    val blocks: List<SetupGuideBlock>,
)

enum class SetupGuideTag {
    Recommended,
    Advanced,
}

sealed interface SetupGuideBlock {
    data class Steps(val values: List<GuideRichText>) : SetupGuideBlock
    data class Paragraph(val value: GuideRichText) : SetupGuideBlock
    data class Warning(val value: GuideRichText) : SetupGuideBlock
    data class Code(val value: String) : SetupGuideBlock
    data class ExternalLink(val label: GuideRichText, val url: String) : SetupGuideBlock
}

object GuideLinks {
    const val WindowsDownload = "https://nextdns.io/download/windows/stable"
    const val MacDownload = "https://nextdns.io/download/macos/stable"
    const val IosAppStore = "https://apps.apple.com/us/app/nextdns/id1463342498"
    const val MacAppStore = "https://apps.apple.com/us/app/nextdns/id1464122853?mt=12"
    const val YogaDns = "https://yogadns.com"
    const val YogaDnsInstructions = "$YogaDns/docs/nextdns"
    const val CliInstaller = "https://nextdns.io/install"
    const val CliWiki = "https://github.com/nextdns/nextdns/wiki"
    const val Tailscale = "https://tailscale.com/kb/1218/nextdns"
    const val UnboundCnameIssue = "https://github.com/NLnetLabs/unbound/issues/132"

    fun iosAppClip(profileId: String): String =
        "https://app.nextdns.io/${Uri.encode(profileId)}"

    fun appleProfile(profileId: String): String =
        "https://apple.nextdns.io/?profile=${Uri.encode(profileId)}"
}


object SetupGuideCatalogFactory {
    private const val Dns4Primary = "45.90.28.0"
    private const val Dns4Secondary = "45.90.30.0"
    private const val Dns6Primary = "2a07:a8c0::"
    private const val Dns6Secondary = "2a07:a8c1::"

    private val guideRoot = listOf("setup", "guide", "platforms")

    fun create(context: Context, content: SetupContent): SetupGuideCatalog {
        val idValues = mapOf(
            "id" to content.profileId,
            "profile" to content.profileId,
        )
        val dotValues = idValues + mapOf("endpoint" to content.dnsOverTls)
        val dohValues = idValues + mapOf("endpoint" to content.dnsOverHttps)
        val linkedIpValues = addressValues(content.linkedIp.servers)
        val ipv6Values = addressValues(content.ipv6)

        return SetupGuideCatalog(
            platforms = listOfNotNull(
                platform(
                    id = "android",
                    title = localeRequired(path("android", "name")),
                    methods = listOfNotNull(
                        adnsMethod(context),
                        localeMethod(
                            platform = "android",
                            guide = "private",
                            title = guideTitle("android", "private"),
                            values = dotValues,
                        ),
                    ),
                ),
                platform(
                    id = "ios",
                    title = localeRequired(path("ios", "name")),
                    methods = listOfNotNull(
                        localeMethod(
                            platform = "ios",
                            guide = "app",
                            title = guideTitle("ios", "app"),
                            values = idValues,
                            links = listOf(
                                externalLink(
                                    context.getString(R.string.setup_open_ios_app_store),
                                    GuideLinks.iosAppClip(content.profileId),
                                ),
                            ),
                            tags = setOf(SetupGuideTag.Recommended),
                        ),
                        localeMethod(
                            platform = "ios",
                            guide = "profile",
                            title = guideTitle("ios", "profile"),
                            values = idValues + mapOf(
                                "link" to "apple.nextdns.io",
                            ),
                            links = listOf(
                                externalLink(
                                    context.getString(R.string.setup_open_configuration_profile),
                                    GuideLinks.appleProfile(content.profileId),
                                ),
                            ),
                        ),
                    ),
                ),
                platform(
                    id = "windows",
                    title = localeRequired(path("windows", "name")),
                    methods = listOfNotNull(
                        windowsDnsOverHttpsMethod(dohValues),
                        localeMethod(
                            platform = "windows",
                            guide = "app",
                            title = guideTitle("windows", "app"),
                            values = idValues + mapOf("link" to GuideLinks.WindowsDownload),
                            links = listOf(
                                externalLink(
                                    context.getString(R.string.setup_download_windows),
                                    GuideLinks.WindowsDownload,
                                ),
                            ),
                            tags = setOf(SetupGuideTag.Recommended)
                        ),

                        ipv6Values?.let {
                            localeMethod(
                                platform = "windows",
                                guide = "ip",
                                title = context.getString(R.string.ipv6),
                                values = it + mapOf("version" to "6"),
                            )
                        },
                        linkedIpValues?.let { values ->

                            localeMethod(
                                platform = "windows",
                                guide = "ip",
                                title = Locales.getString("setup", "guide", "ipv4", "title"),
                                values = values + mapOf("version" to "4"),
                            )
                        },

                        yogaDnsMethod(context, idValues),
                    ),
                ),
                platform(
                    id = "macos",
                    title = localeRequired(path("macos", "name")),
                    methods = listOfNotNull(
                        macosProfileMethod(context, content, idValues),
                        localeMethod(
                            platform = "macos",
                            guide = "app",
                            title = guideTitle("macos", "app"),
                            values = idValues,
                            links = listOf(
                                externalLink(
                                    context.getString(R.string.setup_open_mac_app_store),
                                    GuideLinks.MacDownload,
                                ),
                            ),
                        ),
                        ipv6Values?.let {
                            localeMethod(
                                platform = "macos",
                                guide = "ip",
                                title = context.getString(R.string.ipv6),
                                values = it + mapOf("version" to "6"),
                            )
                        },
                        linkedIpValues?.let { values ->
                            localeMethod(
                                platform = "macos",
                                guide = "ip",
                                title = Locales.getString("setup", "guide", "ipv4", "title"),
                                values = values,
                            )
                        },
                        cliMethod(context, "macos", idValues),
                    ),
                ),
                platform(
                    id = "linux",
                    title = localeRequired(path("linux", "name")),
                    methods = listOfNotNull(
                        systemdResolvedMethod(context, content),
                        cliMethod(context, "linux", idValues),
                        ipv6Values?.let { values ->
                            localeMethod(
                                platform = "linux",
                                guide = "ip",
                                title = context.getString(R.string.ipv6),
                                values = values,
                            )
                        },
                        linkedIpValues?.let { values ->
                            localeMethod(
                                platform = "linux",
                                guide = "ip",
                                title = Locales.getString("setup", "guide", "ipv4", "title"),
                                values = values,
                            )
                        },
                        dnsmasqMethod(context, content),
                        stubbyMethod(context, content),
                        dnscryptMethod(context, content),
                        knotResolverMethod(context, content),
                        cloudflaredMethod(context, content),
                        unboundMethod(context, content),
                    ),
                ),
                platform(
                    id = "chromeos",
                    title = localeRequired(path("chromeos", "name")),
                    methods = listOfNotNull(
                        localeMethod(
                            platform = "chromeos",
                            guide = "secure-dns",
                            title = guideTitle("chromeos", "secure-dns"),
                            values = dohValues,
                            tags = setOf(SetupGuideTag.Recommended),
                        ),
                        ipv6Values?.let {
                            localeMethod(
                                platform = "chromeos",
                                guide = "ip",
                                title = context.getString(R.string.ipv6),
                                values = it,
                            )
                        },
                        linkedIpValues?.let { values ->
                            localeMethod(
                                platform = "chromeos",
                                guide = "ip",
                                title = Locales.getString("setup", "guide", "ipv4", "title"),
                                values = values,
                            )
                        },
                    ),
                ),
                platform(
                    id = "browsers",
                    title = localeRequired(path("browsers", "name")),
                    methods = listOfNotNull(
                        localeMethod("browsers", "chrome", guideTitle("browsers", "chrome"), dohValues),
                        localeMethod("browsers", "firefox", guideTitle("browsers", "firefox"), dohValues),
                        localeMethod("browsers", "edge", guideTitle("browsers", "edge"), dohValues),
                        localeMethod("browsers", "brave", guideTitle("browsers", "brave"), dohValues),
                    ),
                ),
                platform(
                    id = "routers",
                    title = localeRequired(path("routers", "name")),
                    methods = listOfNotNull(
                        localeMethod(
                            platform = "routers",
                            guide = "cli",
                            title = guideTitle("routers", "cli"),
                            values = mapOf("link" to GuideLinks.CliWiki),
                            links = listOf(
                                externalLink(
                                    context.getString(R.string.setup_open_cli_wiki),
                                    GuideLinks.CliWiki,
                                ),
                            ),
                            tags = setOf(SetupGuideTag.Recommended),
                        ),
                        routerIpv6Method(context, ipv6Values, content.ipv6),
                        linkedIpValues?.let { values ->
                            localeMethod(
                                platform = "routers",
                                guide = "ip",
                                title = Locales.getString("setup", "guide", "ipv4", "title"),
                                values = values,
                            )
                        },
                        dnsmasqMethod(context, content),
                        stubbyMethod(context, content),
                        pfSenseMethod(context, content),
                        dnscryptMethod(context, content),
                        knotResolverMethod(context, content),
                        unboundMethod(context, content),
                        mikrotikMethod(context, content),
                        tailscaleMethod(context),
                    ),
                ),
            ),
        )
    }

    private fun adnsMethod(context: Context): SetupGuideMethod = SetupGuideMethod(
        id = "adns",
        title = context.getString(R.string.setup_adns_shizuku),
        requirements = GuideRichText(context.getString(R.string.setup_adns_shizuku_requirements)),
        tags = setOf(SetupGuideTag.Recommended),
        blocks = listOf(
            SetupGuideBlock.Steps(
                listOf(
                    GuideRichText(context.getString(R.string.setup_adns_shizuku_step_1)),
                    GuideRichText(context.getString(R.string.setup_adns_shizuku_step_2)),
                    GuideRichText(context.getString(R.string.setup_adns_shizuku_step_3)),
                ),
            ),
            SetupGuideBlock.ExternalLink(
                GuideRichText(context.getString(R.string.download_adns)),
                "https://github.com/eyalm2000/adns"
            )
        ),

    )

    private fun windowsDnsOverHttpsMethod(
        values: Map<String, String>,
    ): SetupGuideMethod? {
        val basePath = path("windows", "guides", "dns-over-https")
        val templates = normalizeSetupSteps(localeNode(basePath + "steps"))
        if (templates.isEmpty()) return null

        return SetupGuideMethod(
            id = "dns-over-https",
            title = guideTitle("windows", "dns-over-https"),
            requirements = localeString(basePath + "requirements")?.let {
                GuideRichText(it, values)
            },
            tags = setOf(SetupGuideTag.Recommended),
            blocks = listOf(
                SetupGuideBlock.Steps(
                    templates.mapIndexed { index, template ->
                        val dnsServer = if (index == templates.lastIndex - 1) {
                            Dns4Secondary
                        } else {
                            Dns4Primary
                        }
                        GuideRichText(template, values + ("ip" to dnsServer) + ("version" to "4"))
                    }
                )
            ),
        )
    }

    private fun yogaDnsMethod(
        context: Context,
        values: Map<String, String>,
    ): SetupGuideMethod? {
        val basePath = path("windows", "guides", "yoga")
        val templates = normalizeSetupSteps(localeNode(basePath + "steps"))
        if (templates.isEmpty()) return null

        return SetupGuideMethod(
            id = "yoga",
            title = context.getString(R.string.setup_yogadns),
            tags = setOf(SetupGuideTag.Advanced),
            blocks = listOf(
                SetupGuideBlock.Steps(
                    templates.mapIndexed { index, template ->
                        val link = if (index == 0) GuideLinks.YogaDns else GuideLinks.YogaDnsInstructions
                        GuideRichText(template, values + ("link" to link))
                    }
                ),
                externalLink(context.getString(R.string.setup_download_yogadns), GuideLinks.YogaDns),
                externalLink(
                    context.getString(R.string.setup_open_yogadns_instructions),
                    GuideLinks.YogaDnsInstructions,
                ),
            ),
        )
    }

    private fun macosProfileMethod(
        context: Context,
        content: SetupContent,
        values: Map<String, String>,
    ): SetupGuideMethod? {
        val base = localeMethod(
            platform = "ios",
            guide = "profile",
            title = guideTitle("ios", "profile"),
            values = values + mapOf("link" to "apple.nextdns.io"),
            links = listOf(
                externalLink(
                    context.getString(R.string.setup_open_configuration_profile),
                    GuideLinks.appleProfile(content.profileId),
                ),
            ),
            tags = setOf(SetupGuideTag.Recommended),
        ) ?: return null

        return base.copy(
            requirements = localeString(path("macos", "guides", "profile", "requirements"))?.let {
                GuideRichText(it, values)
            },
        )
    }

    private fun cliMethod(
        context: Context,
        platform: String,
        values: Map<String, String>,
    ): SetupGuideMethod? = localeMethod(
        platform = platform,
        guide = "cli",
        title = guideTitle(platform, "cli"),
        values = values + mapOf(
            "command" to "sh -c \"\$(curl -sL ${GuideLinks.CliInstaller})\"",
            "link" to GuideLinks.CliWiki,
        ),
        links = listOf(externalLink(context.getString(R.string.setup_open_cli_wiki), GuideLinks.CliWiki)),
        tags = setOf(SetupGuideTag.Advanced),
    )

    private fun systemdResolvedMethod(
        context: Context,
        content: SetupContent,
    ): SetupGuideMethod = codeMethod(
        id = "systemd-resolved",
        title = context.getString(R.string.setup_systemd_resolved),
        prelude = useFile("/etc/systemd/resolved.conf"),
        code = """
            [Resolve]
            DNS=$Dns4Primary#${content.dnsOverTls}
            DNS=$Dns6Primary#${content.dnsOverTls}
            DNS=$Dns4Secondary#${content.dnsOverTls}
            DNS=$Dns6Secondary#${content.dnsOverTls}
            DNSOverTLS=yes
        """.trimIndent(),
        tags = setOf(SetupGuideTag.Recommended),
    )

    private fun dnsmasqMethod(context: Context, content: SetupContent): SetupGuideMethod = codeMethod(
        id = "dnsmasq",
        title = context.getString(R.string.setup_dnsmasq),
        prelude = useFile("dnsmasq.conf"),
        code = """
            no-resolv
            bogus-priv
            strict-order
            server=$Dns6Secondary
            server=$Dns4Secondary
            server=$Dns6Primary
            server=$Dns4Primary
            add-cpe-id=${content.profileId}
        """.trimIndent(),
    )

    private fun stubbyMethod(context: Context, content: SetupContent): SetupGuideMethod = codeMethod(
        id = "stubby",
        title = context.getString(R.string.setup_stubby),
        prelude = useFile("stubby.yml"),
        code = """
            round_robin_upstreams: 1
            upstream_recursive_servers:
              - address_data: $Dns4Primary
                tls_auth_name: "${content.dnsOverTls}"
              - address_data: ${Dns6Primary}0
                tls_auth_name: "${content.dnsOverTls}"
              - address_data: $Dns4Secondary
                tls_auth_name: "${content.dnsOverTls}"
              - address_data: ${Dns6Secondary}0
                tls_auth_name: "${content.dnsOverTls}"
        """.trimIndent(),
        warning = localeString(path("linux", "guides", "stubby", "warning"))?.let(::GuideRichText),
    )

    private fun dnscryptMethod(context: Context, content: SetupContent): SetupGuideMethod? {
        val stamp = content.dnscryptStamp?.takeIf(String::isNotBlank) ?: return null
        return codeMethod(
            id = "dnscrypt",
            title = context.getString(R.string.dnscrypt),
            prelude = useFile("dnscrypt-proxy.toml"),
            code = """
                server_names = ['NextDNS-${content.profileId}']

                [static]
                  [static.'NextDNS-${content.profileId}']
                  stamp = '$stamp'
            """.trimIndent(),
        )
    }

    private fun knotResolverMethod(context: Context, content: SetupContent): SetupGuideMethod = codeMethod(
        id = "knot-resolver",
        title = context.getString(R.string.setup_knot_resolver),
        prelude = useFile("/etc/kresd/custom.conf"),
        code = """
            policy.add(policy.all(policy.TLS_FORWARD({
              {'$Dns4Primary', hostname='${content.dnsOverTls}'},
              {'$Dns6Primary', hostname='${content.dnsOverTls}'},
              {'$Dns4Secondary', hostname='${content.dnsOverTls}'},
              {'$Dns6Secondary', hostname='${content.dnsOverTls}'}
            })))
        """.trimIndent(),
    )

    private fun cloudflaredMethod(context: Context, content: SetupContent): SetupGuideMethod = codeMethod(
        id = "cloudflared",
        title = context.getString(R.string.setup_cloudflared),
        prelude = useFile("/usr/local/etc/cloudflared/config.yml"),
        code = """
            proxy-dns: true
            proxy-dns-upstream:
             - ${content.dnsOverHttps}
        """.trimIndent(),
    )

    private fun unboundMethod(context: Context, content: SetupContent): SetupGuideMethod = codeMethod(
        id = "unbound",
        title = context.getString(R.string.setup_unbound),
        prelude = useFile("unbound.conf"),
        code = """
            forward-zone:
              name: "."
              forward-tls-upstream: yes
              forward-addr: $Dns4Primary#${content.dnsOverTls}
              forward-addr: $Dns6Primary#${content.dnsOverTls}
              forward-addr: $Dns4Secondary#${content.dnsOverTls}
              forward-addr: $Dns6Secondary#${content.dnsOverTls}
        """.trimIndent(),
        warning = localeString(path("linux", "guides", "unbound", "warning"))?.let {
            GuideRichText(it, mapOf("link" to GuideLinks.UnboundCnameIssue))
        },
        links = listOf(externalLink(context.getString(R.string.setup_open_unbound_issue), GuideLinks.UnboundCnameIssue)),
    )

    private fun routerIpv6Method(
        context: Context,
        values: Map<String, String>?,
        addresses: List<String>,
    ): SetupGuideMethod? {
        val ipv6Values = values ?: return null
        val warningValues = ipv6Values + mapOf(
            "dns1" to addresses.getOrNull(0)?.expandedIpv6().orEmpty(),
            "dns2" to addresses.getOrNull(1)?.expandedIpv6().orEmpty(),
        )
        return localeMethod(
            platform = "routers",
            guide = "ip",
            title = context.getString(R.string.ipv6),
            values = ipv6Values,
            extraBlocks = listOfNotNull(
                localeString(path("routers", "guides", "ip", "ipv6", "warning"))?.let {
                    SetupGuideBlock.Warning(GuideRichText(it, warningValues))
                },
            ),
        )
    }

    private fun pfSenseMethod(context: Context, content: SetupContent): SetupGuideMethod? {
        val title = context.getString(R.string.setup_pfsense)
        val base = localeMethod(
            platform = "routers",
            guide = "pfsense",
            title = title,
            values = emptyMap(),
        ) ?: return null
        return base.copy(
            blocks = base.blocks + SetupGuideBlock.Code(
                """
                    server:
                      forward-zone:
                        name: "."
                        forward-tls-upstream: yes
                        forward-addr: $Dns4Primary#${content.dnsOverTls}
                        forward-addr: $Dns6Primary#${content.dnsOverTls}
                        forward-addr: $Dns4Secondary#${content.dnsOverTls}
                        forward-addr: $Dns6Secondary#${content.dnsOverTls}
                """.trimIndent(),
            ),
        )
    }

    private fun mikrotikMethod(context: Context, content: SetupContent): SetupGuideMethod = SetupGuideMethod(
        id = "mikrotik",
        title = context.getString(R.string.setup_mikrotik),
        blocks = listOf(
            SetupGuideBlock.Paragraph(
                GuideRichText(localeRequired(path("routers", "guides", "mikrotik", "run"))),
            ),
            SetupGuideBlock.Code(
                """
                    /tool fetch url=https://curl.se/ca/cacert.pem
                    /certificate import file-name=cacert.pem
                    /ip dns set servers=""
                    /ip dns static add name=dns.nextdns.io address=$Dns4Primary type=A
                    /ip dns static add name=dns.nextdns.io address=$Dns4Secondary type=A
                    /ip dns static add name=dns.nextdns.io address=$Dns6Primary type=AAAA
                    /ip dns static add name=dns.nextdns.io address=$Dns6Secondary type=AAAA
                    /ip dns set use-doh-server="${content.dnsOverHttps}" verify-doh-cert=yes
                """.trimIndent(),
            ),
        ),
    )

    private fun tailscaleMethod(context: Context): SetupGuideMethod = SetupGuideMethod(
        id = "tailscale",
        title = context.getString(R.string.setup_tailscale),
        blocks = listOf(
            SetupGuideBlock.Paragraph(
                GuideRichText(
                    localeRequired(path("vpn", "guides", "tailscale", "manual")),
                    mapOf("link" to GuideLinks.Tailscale),
                ),
            ),
            externalLink(context.getString(R.string.setup_open_tailscale_guide), GuideLinks.Tailscale),
        ),
    )

    private fun codeMethod(
        id: String,
        title: String,
        prelude: GuideRichText,
        code: String,
        warning: GuideRichText? = null,
        links: List<SetupGuideBlock.ExternalLink> = emptyList(),
        tags: Set<SetupGuideTag> = emptySet(),
    ): SetupGuideMethod = SetupGuideMethod(
        id = id,
        title = title,
        tags = tags,
        blocks = buildList {
            add(SetupGuideBlock.Paragraph(prelude))
            add(SetupGuideBlock.Code(code))
            warning?.let { add(SetupGuideBlock.Warning(it)) }
            addAll(links)
        },
    )

    private fun localeMethod(
        platform: String,
        guide: String,
        title: String,
        values: Map<String, String>,
        links: List<SetupGuideBlock.ExternalLink> = emptyList(),
        extraBlocks: List<SetupGuideBlock> = emptyList(),
        tags: Set<SetupGuideTag> = emptySet(),
    ): SetupGuideMethod? {
        val basePath = path(platform, "guides", guide)
        val blocks = localeBlocks(basePath, values) + extraBlocks + links
        if (blocks.isEmpty()) return null

        return SetupGuideMethod(
            id = guide,
            title = title,
            requirements = localeString(basePath + "requirements")?.let {
                GuideRichText(it, values)
            },
            tags = tags,
            blocks = blocks,
        )
    }

    private fun platform(
        id: String,
        title: String,
        methods: List<SetupGuideMethod>,
    ): SetupGuidePlatform? = methods.takeIf { it.isNotEmpty() }?.let {
        SetupGuidePlatform(id = id, title = title, methods = it)
    }

    private fun localeBlocks(
        basePath: List<String>,
        values: Map<String, String>,
    ): List<SetupGuideBlock> = buildList {
        normalizeSetupSteps(localeNode(basePath + "steps"))
            .takeIf { it.isNotEmpty() }
            ?.let { steps -> add(SetupGuideBlock.Steps(steps.map { GuideRichText(it, values) })) }
        localeString(basePath + "step")?.let { step ->
            add(SetupGuideBlock.Steps(listOf(GuideRichText(step, values))))
        }
        localeString(basePath + "manual")?.let { manual ->
            add(SetupGuideBlock.Paragraph(GuideRichText(manual, values)))
        }
        localeString(basePath + "warning")?.let { warning ->
            add(SetupGuideBlock.Warning(GuideRichText(warning, values)))
        }
        localeString(basePath + "qrText")?.let { qrText ->
            add(SetupGuideBlock.Paragraph(GuideRichText(qrText, values)))
        }
    }

    private fun externalLink(label: String, url: String): SetupGuideBlock.ExternalLink =
        SetupGuideBlock.ExternalLink(GuideRichText(label), url)

    private fun useFile(file: String): GuideRichText = GuideRichText(
        Locales.getString("setup", "guide", "use"),
        mapOf("file" to file),
    )

    private fun guideTitle(platform: String, guide: String): String =
        localeRequired(path(platform, "guides", guide, "title"))

    private fun addressValues(addresses: List<String>): Map<String, String>? =
        addresses.takeIf { it.size >= 2 }?.let {
            mapOf("dns1" to it[0], "dns2" to it[1])
        }

    private fun localeNode(path: List<String>): Any? =
        Locales.getNode(*(guideRoot + path).toTypedArray())

    private fun localeString(path: List<String>): String? = localeNode(path) as? String

    private fun localeRequired(path: List<String>): String =
        Locales.getString(*(guideRoot + path).toTypedArray())

    private fun path(vararg values: String): List<String> = values.toList()
}

internal fun normalizeSetupSteps(value: Any?): List<String> = when (value) {
    is Map<*, *> -> value.entries
        .sortedBy { entry -> entry.key.toString().toIntOrNull() ?: Int.MAX_VALUE }
        .mapNotNull { entry -> entry.value as? String }

    is List<*> -> value.filterIsInstance<String>()
    is String -> listOf(value)
    else -> emptyList()
}

private fun String.expandedIpv6(): String = runCatching {
    val bytes = InetAddress.getByName(this).address
    if (bytes.size != 16) return@runCatching this
    bytes.indices
        .step(2)
        .joinToString(":") { index ->
            val group = ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
            group.toString(16).padStart(4, '0')
        }
}.getOrDefault(this)
