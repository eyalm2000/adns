package com.eyalm.adns.data.nextdns.resources

import android.content.Context
import com.eyalm.adns.R
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.nextdns.model.ListIcon
import com.eyalm.adns.data.nextdns.settings.FeatureMaturity

data class NextDnsResourceItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val icon: ListIcon = ListIcon.None,
    val website: String? = null,
    val entries: Int? = null,
    val updatedOn: String? = null,
    val spamhausRank: Int = 0,
    val sourceIndex: Int = 0,
)

enum class NextDnsResourceSource {
    SERVER,
    LOCALE,
}

data class NextDnsResourceSpec(
    val apiPage: String,
    val apiFeature: String,
    val localeCategory: String,
    val localeKey: String,
    val source: NextDnsResourceSource,
    val localePath: List<String>,
    val parentPage: ParentPage? = null,
    val customTitleRes: Int? = null,
    val customDescriptionRes: Int? = null,
    val customDescription: String? = null,
    val allowsCustomInput: Boolean = false,
    val maturity: FeatureMaturity = FeatureMaturity.STABLE,
) {
    enum class ParentPage { SECURITY, PRIVACY, PARENTAL_CONTROL }

    val isBeta: Boolean get() = maturity == FeatureMaturity.BETA
    val beta: Boolean get() = isBeta

    constructor(
        apiPage: String,
        apiFeature: String,
        localeCategory: String,
        localeKey: String,
        source: NextDnsResourceSource,
        localePath: List<String>,
        parentPage: ParentPage? = null,
        customTitleRes: Int? = null,
        customDescriptionRes: Int? = null,
        customDescription: String? = null,
        allowsCustomInput: Boolean = false,
        isBeta: Boolean,
    ) : this(
        apiPage = apiPage,
        apiFeature = apiFeature,
        localeCategory = localeCategory,
        localeKey = localeKey,
        source = source,
        localePath = localePath,
        parentPage = parentPage,
        customTitleRes = customTitleRes,
        customDescriptionRes = customDescriptionRes,
        customDescription = customDescription,
        allowsCustomInput = allowsCustomInput,
        maturity = if (isBeta) FeatureMaturity.BETA else FeatureMaturity.STABLE,
    )

    fun title(context: Context): String =
        customTitleRes?.let(context::getString)
            ?: Locales.getString(localeCategory, localeKey, "name")

    fun description(context: Context): String =
        customDescriptionRes?.let(context::getString)
            ?: customDescription
            ?: Locales.getString(localeCategory, localeKey, "description")
}

object NextDnsResourceRegistry {
    val security = listOf(
        NextDnsResourceSpec(
            apiPage = "security",
            apiFeature = "tlds",
            localeCategory = "security",
            localeKey = "tld",
            source = NextDnsResourceSource.SERVER,
            localePath = emptyList(),
            parentPage = NextDnsResourceSpec.ParentPage.SECURITY,
        ),
    )

    val privacy = listOf(
        NextDnsResourceSpec(
            apiPage = "privacy",
            apiFeature = "blocklists",
            localeCategory = "privacy",
            localeKey = "blocklists",
            source = NextDnsResourceSource.SERVER,
            localePath = emptyList(),
            parentPage = NextDnsResourceSpec.ParentPage.PRIVACY,
        ),
        NextDnsResourceSpec(
            apiPage = "privacy",
            apiFeature = "natives",
            localeCategory = "privacy",
            localeKey = "native",
            source = NextDnsResourceSource.LOCALE,
            localePath = listOf("privacy", "native", "systems"),
            parentPage = NextDnsResourceSpec.ParentPage.PRIVACY,
            isBeta = true,
        ),
    )

    val parentalControl = listOf(
        NextDnsResourceSpec(
            apiPage = "parentalControl",
            apiFeature = "services",
            localeCategory = "parentalControl",
            localeKey = "services",
            source = NextDnsResourceSource.SERVER,
            localePath = listOf("parentalControl", "services", "services"),
            parentPage = NextDnsResourceSpec.ParentPage.PARENTAL_CONTROL,
        ),
        NextDnsResourceSpec(
            apiPage = "parentalControl",
            apiFeature = "categories",
            localeCategory = "parentalControl",
            localeKey = "categories",
            source = NextDnsResourceSource.LOCALE,
            localePath = listOf("parentalControl", "categories", "categories"),
            parentPage = NextDnsResourceSpec.ParentPage.PARENTAL_CONTROL,
        ),
    )

    val denylist = NextDnsResourceSpec(
        apiPage = "denylist",
        apiFeature = "",
        localeCategory = "pages",
        localeKey = "denylist",
        source = NextDnsResourceSource.SERVER,
        localePath = emptyList(),
        customTitleRes = R.string.denylist,
        customDescription = Locales.getString("xlist", "denylist", "info"),
        allowsCustomInput = true,
    )

    val allowlist = NextDnsResourceSpec(
        apiPage = "allowlist",
        apiFeature = "",
        localeCategory = "pages",
        localeKey = "allowlist",
        source = NextDnsResourceSource.SERVER,
        localePath = emptyList(),
        customTitleRes = R.string.allowlist,
        customDescription = Locales.getString("xlist", "allowlist", "info"),
        allowsCustomInput = true,
    )
}
