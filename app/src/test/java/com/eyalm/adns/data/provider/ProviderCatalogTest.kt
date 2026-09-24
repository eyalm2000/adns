package com.eyalm.adns.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {
    @Test
    fun `bundled catalog resolves documented presets`() {
        val catalog = DnsProviderCatalog.default

        assertTrue(catalog.validationProblems().isEmpty())
        assertEquals(
            "dns.adguard-dns.com",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("adguard"),
                    presetId = ResolverPresetId("default"),
                )
            )
        )
        assertEquals(
            "security.cloudflare-dns.com",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("cloudflare"),
                    presetId = ResolverPresetId("security"),
                )
            )
        )
        assertEquals(
            "dns.google",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("google"),
                    presetId = ResolverPresetId("default"),
                )
            )
        )
        assertEquals(
            "dns11.quad9.net",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("quad9"),
                    presetId = ResolverPresetId("secured-edns"),
                )
            )
        )
        assertEquals(
            "dns.familyshield.opendns.com",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("opendns"),
                    presetId = ResolverPresetId("family"),
                )
            )
        )
        assertEquals(
            "dnsforge.de",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("dnsforge"),
                    presetId = ResolverPresetId("normal"),
                )
            )
        )
        assertEquals(
            "clean.dnsforge.de",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("dnsforge"),
                    presetId = ResolverPresetId("clean"),
                )
            )
        )
        assertEquals(
            "hard.dnsforge.de",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("dnsforge"),
                    presetId = ResolverPresetId("hard"),
                )
            )
        )
        assertEquals(
            "blank.dnsforge.de",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("dnsforge"),
                    presetId = ResolverPresetId("blank"),
                )
            )
        )
        assertEquals(
            "root.hagezi.org",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("hagezi"),
                    presetId = ResolverPresetId("root"),
                )
            )
        )
        assertEquals(
            "wurzn.hagezi.org",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("hagezi"),
                    presetId = ResolverPresetId("wurzn"),
                )
            )
        )
        assertEquals(
            "juuri.hagezi.org",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("hagezi"),
                    presetId = ResolverPresetId("juuri"),
                )
            )
        )
        assertEquals(
            "ctif.hagezi.org",
            catalog.resolveHostname(
                DnsProviderSelection.Standard(
                    providerId = ProviderId("hagezi"),
                    presetId = ResolverPresetId("ctif"),
                )
            )
        )
    }

    @Test
    fun `catalog reports duplicate provider ids`() {
        val provider = provider(id = "duplicate")
        val catalog = DnsProviderCatalog(
            standardProviders = listOf(provider, provider.copy()),
            enhancedProviders = emptyList(),
        )

        assertTrue(
            catalog.validationProblems().contains(
                ProviderCatalogProblem.DuplicateProviderId(ProviderId("duplicate"))
            )
        )
    }

    @Test
    fun `catalog reports invalid preset structure`() {
        val invalid = provider(
            id = "invalid",
            defaultPresetId = "missing",
            presets = listOf(
                preset(id = "same", hostname = "dns.example"),
                preset(id = "same", hostname = "https://not-a-hostname"),
            ),
        )
        val empty = provider(id = "empty", presets = emptyList())
        val catalog = DnsProviderCatalog(
            standardProviders = listOf(invalid, empty),
            enhancedProviders = emptyList(),
        )

        val problems = catalog.validationProblems()
        assertTrue(
            problems.contains(
                ProviderCatalogProblem.MissingDefaultPreset(
                    providerId = ProviderId("invalid"),
                    presetId = ResolverPresetId("missing"),
                )
            )
        )
        assertTrue(
            problems.contains(
                ProviderCatalogProblem.DuplicatePresetId(
                    providerId = ProviderId("invalid"),
                    presetId = ResolverPresetId("same"),
                )
            )
        )
        assertTrue(
            problems.any {
                it is ProviderCatalogProblem.InvalidHostname &&
                    it.hostname == "https://not-a-hostname"
            }
        )
        assertTrue(
            problems.contains(
                ProviderCatalogProblem.EmptyPresetList(ProviderId("empty"))
            )
        )
    }

    @Test
    fun `catalog reports contradictory metadata for the same hostname`() {
        val catalog = DnsProviderCatalog(
            standardProviders = listOf(
                provider(
                    presets = listOf(
                        preset(
                            id = "one",
                            hostname = "dns.example",
                            features = setOf(ResolverFeature.Ads),
                        ),
                        preset(
                            id = "two",
                            hostname = "dns.example",
                            features = emptySet(),
                        ),
                    )
                )
            ),
            enhancedProviders = emptyList(),
        )

        assertTrue(
            catalog.validationProblems().contains(
                ProviderCatalogProblem.ConflictingHostnameMetadata("dns.example")
            )
        )
    }

    @Test
    fun `hostname parser normalizes unicode and rejects non-hostnames`() {
        assertEquals(
            "dns.xn--nqv7f",
            PrivateDnsHostname.parse("DNS.机构.")?.ascii
        )
        assertNull(PrivateDnsHostname.parse("https://dns.example"))
        assertNull(PrivateDnsHostname.parse("1.1.1.1"))
        assertNull(PrivateDnsHostname.parse("single-label"))
        assertNull(PrivateDnsHostname.parse("-bad.example"))
    }

    private fun provider(
        id: String = "provider",
        defaultPresetId: String = "default",
        presets: List<ResolverPreset> = listOf(preset()),
    ) = StandardProviderDefinition(
        id = ProviderId(id),
        titleRes = 1,
        descriptionRes = 2,
        defaultPresetId = ResolverPresetId(defaultPresetId),
        presets = presets,
    )

    private fun preset(
        id: String = "default",
        hostname: String = "dns.example",
        features: Set<ResolverFeature> = emptySet(),
    ) = ResolverPreset(
        id = ResolverPresetId(id),
        hostname = hostname,
        titleRes = 1,
        descriptionRes = 2,
        features = features,
    )
}
