package com.eyalm.adns.data.nextdns.api

import com.eyalm.adns.data.account.AccountCredentialRef
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Network interceptor that attaches the API key of the credential the request was issued
 * with (see [AccountRequestTaggingInterceptor]), never the key of whichever account is
 * active when the request reaches the network.
 */
internal class NextDnsApiKeyInterceptor(
    private val apiKeyProvider: (AccountCredentialRef?) -> String?,
    private val allowedHosts: Set<String>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()
            .removeHeader(API_KEY_HEADER)
        apiKeyForHost(originalRequest.url.host, originalRequest.accountRef())?.let { key ->
            builder.header(API_KEY_HEADER, key)
        }
        return chain.proceed(builder.build())
    }

    internal fun apiKeyForHost(host: String, ref: AccountCredentialRef? = null): String? {
        if (host !in allowedHosts) return null
        return apiKeyProvider(ref)
    }

    private companion object {
        const val API_KEY_HEADER = "X-Api-Key"
    }
}

internal fun Request.accountRef(): AccountCredentialRef? =
    tag(AccountCredentialRef::class.java)
