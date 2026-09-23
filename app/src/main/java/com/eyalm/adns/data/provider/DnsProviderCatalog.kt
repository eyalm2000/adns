package com.eyalm.adns.data.provider

import com.eyalm.adns.R
import java.util.Locale

class DnsProviderCatalog(
    val standardProviders: List<StandardProviderDefinition>,
    val enhancedProviders: List<EnhancedProviderDefinition>,
) {
    val providers: List<ProviderDefinition> = enhancedProviders + standardProviders

    fun provider(id: ProviderId): ProviderDefinition? =
        providers.firstOrNull { it.id == id }

    fun standardProvider(id: ProviderId): StandardProviderDefinition? =
        standardProviders.firstOrNull { it.id == id }

    fun provider(selection: DnsProviderSelection): ProviderDefinition? =
        provider(selection.providerId)

    fun resolveHostname(selection: DnsProviderSelection): String? = when (selection) {
        is DnsProviderSelection.Standard -> standardProvider(selection.providerId)
            ?.presets
            ?.firstOrNull { it.id == selection.presetId }
            ?.hostname

        is DnsProviderSelection.Custom -> selection.hostname
        is DnsProviderSelection.Enhanced -> null
    }

    fun validationProblems(): List<ProviderCatalogProblem> {
        val problems = mutableListOf<ProviderCatalogProblem>()
        providers
            .groupBy(ProviderDefinition::id)
            .filterValues { it.size > 1 }
            .keys
            .forEach { problems += ProviderCatalogProblem.DuplicateProviderId(it) }

        standardProviders.forEach { provider ->
            if (provider.presets.isEmpty()) {
                problems += ProviderCatalogProblem.EmptyPresetList(provider.id)
            }
            if (provider.presets.none { it.id == provider.defaultPresetId }) {
                problems += ProviderCatalogProblem.MissingDefaultPreset(
                    providerId = provider.id,
                    presetId = provider.defaultPresetId,
                )
            }
            provider.presets
                .groupBy(ResolverPreset::id)
                .filterValues { it.size > 1 }
                .keys
                .forEach { presetId ->
                    problems += ProviderCatalogProblem.DuplicatePresetId(
                        providerId = provider.id,
                        presetId = presetId,
                    )
                }
            provider.presets.forEach { preset ->
                if (PrivateDnsHostname.parse(preset.hostname) == null) {
                    problems += ProviderCatalogProblem.InvalidHostname(
                        providerId = provider.id,
                        presetId = preset.id,
                        hostname = preset.hostname,
                    )
                }
            }
        }

        standardProviders
            .flatMap(StandardProviderDefinition::presets)
            .mapNotNull { preset ->
                PrivateDnsHostname.parse(preset.hostname)?.ascii?.let {
                    it.lowercase(Locale.ROOT) to preset.features
                }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .filterValues { featureSets -> featureSets.distinct().size > 1 }
            .keys
            .forEach { hostname ->
                problems += ProviderCatalogProblem.ConflictingHostnameMetadata(hostname)
            }

        return problems.distinct()
    }

    companion object {
        val ADGUARD = ProviderId("adguard")
        val CLOUDFLARE = ProviderId("cloudflare")
        val GOOGLE = ProviderId("google")
        val NEXTDNS = ProviderId("nextdns")
        val QUAD9 = ProviderId("quad9")
        val OPENDNS = ProviderId("opendns")
        val DNSFORGE = ProviderId("dnsforge")


        val default: DnsProviderCatalog = DnsProviderCatalog(
            standardProviders = listOf(
                StandardProviderDefinition(
                    id = ADGUARD,
                    titleRes = R.string.adguard_dns,
                    descriptionRes = R.string.the_public_adguard_dns_server_blocks_ads_and_trackers,
                    defaultPresetId = ResolverPresetId("default"),
                    presets = listOf(
                        ResolverPreset(
                            id = ResolverPresetId("default"),
                            hostname = "dns.adguard-dns.com",
                            titleRes = R.string.provider_preset_adguard_default,
                            descriptionRes = R.string.provider_preset_adguard_default_description,
                            features = setOf(ResolverFeature.Ads, ResolverFeature.Trackers),
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("unfiltered"),
                            hostname = "unfiltered.adguard-dns.com",
                            titleRes = R.string.provider_preset_adguard_unfiltered,
                            descriptionRes = R.string.provider_preset_adguard_unfiltered_description,
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("family"),
                            hostname = "family.adguard-dns.com",
                            titleRes = R.string.provider_preset_adguard_family,
                            descriptionRes = R.string.provider_preset_adguard_family_description,
                            features = setOf(
                                ResolverFeature.Ads,
                                ResolverFeature.Trackers,
                                ResolverFeature.AdultContent,
                                ResolverFeature.SafeSearch,
                            ),
                        ),
                    ),
                ),
                StandardProviderDefinition(
                    id = GOOGLE,
                    titleRes = R.string.google_dns,
                    descriptionRes = R.string.the_public_google_dns_server,
                    defaultPresetId = ResolverPresetId("default"),
                    presets = listOf(
                        ResolverPreset(
                            id = ResolverPresetId("default"),
                            hostname = "dns.google",
                            titleRes = R.string.provider_preset_google_standard,
                            descriptionRes = R.string.provider_preset_google_standard_description,
                        )
                    ),
                ),
                StandardProviderDefinition(
                    id = CLOUDFLARE,
                    titleRes = R.string.cloudflare_dns,
                    descriptionRes = R.string.the_public_cloudflare_dns_server,
                    defaultPresetId = ResolverPresetId("standard"),
                    presets = listOf(
                        ResolverPreset(
                            id = ResolverPresetId("standard"),
                            hostname = "one.one.one.one",
                            titleRes = R.string.provider_preset_cloudflare_standard,
                            descriptionRes = R.string.provider_preset_cloudflare_standard_description,
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("security"),
                            hostname = "security.cloudflare-dns.com",
                            titleRes = R.string.provider_preset_cloudflare_security,
                            descriptionRes = R.string.provider_preset_cloudflare_security_description,
                            features = setOf(ResolverFeature.Malware),
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("family"),
                            hostname = "family.cloudflare-dns.com",
                            titleRes = R.string.provider_preset_cloudflare_family,
                            descriptionRes = R.string.provider_preset_cloudflare_family_description,
                            features = setOf(
                                ResolverFeature.Malware,
                                ResolverFeature.AdultContent,
                            ),
                        ),
                    ),
                ),
                StandardProviderDefinition(
                    id = QUAD9,
                    titleRes = R.string.quad9,
                    descriptionRes = R.string.the_public_quad9_dns_server,
                    defaultPresetId = ResolverPresetId("secured"),
                    presets = listOf(
                        ResolverPreset(
                            id = ResolverPresetId("secured"),
                            hostname = "dns.quad9.net",
                            titleRes = R.string.secured,
                            descriptionRes = R.string.blocks_malware_and_phishing,
                            features = setOf(ResolverFeature.Malware)
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("secured-edns"),
                            hostname = "dns11.quad9.net",
                            titleRes = R.string.secured_with_ecs,
                            descriptionRes = R.string.ncludes_malware_blocking_and_edns_client_subnet_for_faster_localized_routing,
                            features = setOf(ResolverFeature.Malware)
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("unsecured"),
                            hostname = "dns10.quad9.net",
                            titleRes = R.string.unsecured,
                            descriptionRes = R.string.standard_dns_resolution
                        )

                    ),
                ),
                StandardProviderDefinition(
                    id = OPENDNS,
                    titleRes = R.string.opendns,
                    descriptionRes = R.string.the_public_opendns_dns_server,
                    defaultPresetId = ResolverPresetId("standard"),
                    presets = listOf(
                        ResolverPreset(
                            id = ResolverPresetId("standard"),
                            hostname = "dns.opendns.com",
                            titleRes = R.string.provider_preset_opendns_standard,
                            descriptionRes = R.string.provider_preset_opendns_standard_description,
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("family"),
                            hostname = "dns.familyshield.opendns.com",
                            titleRes = R.string.provider_preset_opendns_family,
                            descriptionRes = R.string.provider_preset_opendns_family_description,
                            features = setOf(ResolverFeature.AdultContent),
                        ),
                    ),
                ),
                StandardProviderDefinition(
                    id = DNSFORGE,
                    titleRes = R.string.dnsforge,
                    descriptionRes = R.string.the_public_dnsforge_dns_server,
                    defaultPresetId = ResolverPresetId("normal"),
                    presets = listOf(
                        ResolverPreset(
                            id = ResolverPresetId("normal"),
                            hostname = "dnsforge.de",
                            titleRes = R.string.provider_preset_dnsforge_normal,
                            descriptionRes = R.string.provider_preset_dnsforge_normal_description,
                            features = setOf(
                                ResolverFeature.Ads,
                                ResolverFeature.Trackers,
                                ResolverFeature.Malware,
                            ),
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("clean"),
                            hostname = "clean.dnsforge.de",
                            titleRes = R.string.provider_preset_dnsforge_clean,
                            descriptionRes = R.string.provider_preset_dnsforge_clean_description,
                            features = setOf(
                                ResolverFeature.Ads,
                                ResolverFeature.Trackers,
                                ResolverFeature.Malware,
                                ResolverFeature.AdultContent,
                                ResolverFeature.SafeSearch,
                            ),
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("hard"),
                            hostname = "hard.dnsforge.de",
                            titleRes = R.string.provider_preset_dnsforge_hard,
                            descriptionRes = R.string.provider_preset_dnsforge_hard_description,
                            features = setOf(
                                ResolverFeature.Ads,
                                ResolverFeature.Trackers,
                                ResolverFeature.Malware,
                            ),
                        ),
                        ResolverPreset(
                            id = ResolverPresetId("blank"),
                            hostname = "blank.dnsforge.de",
                            titleRes = R.string.provider_preset_dnsforge_blank,
                            descriptionRes = R.string.provider_preset_dnsforge_blank_description,
                        ),
                    ),
                ),
            ),
            enhancedProviders = listOf(
                EnhancedProviderDefinition(
                    id = NEXTDNS,
                    titleRes = R.string.nextdns_name,
                    descriptionRes = R.string.connect_your_account_to_use_nextdns_as_a_dns_provider,
                )
            ),
        ).also { catalog ->
            require(catalog.validationProblems().isEmpty()) {
                "Invalid bundled DNS provider catalog: ${catalog.validationProblems()}"
            }
        }
    }
}

sealed interface ProviderCatalogProblem {
    data class DuplicateProviderId(val providerId: ProviderId) : ProviderCatalogProblem

    data class DuplicatePresetId(
        val providerId: ProviderId,
        val presetId: ResolverPresetId,
    ) : ProviderCatalogProblem

    data class MissingDefaultPreset(
        val providerId: ProviderId,
        val presetId: ResolverPresetId,
    ) : ProviderCatalogProblem

    data class EmptyPresetList(val providerId: ProviderId) : ProviderCatalogProblem

    data class InvalidHostname(
        val providerId: ProviderId,
        val presetId: ResolverPresetId,
        val hostname: String,
    ) : ProviderCatalogProblem

    data class ConflictingHostnameMetadata(val hostname: String) : ProviderCatalogProblem
}
