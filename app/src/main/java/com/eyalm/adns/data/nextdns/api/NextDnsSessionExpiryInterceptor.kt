package com.eyalm.adns.data.nextdns.api

import com.eyalm.adns.data.account.AccountCredentialRef
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reports authentication failures together with the credential the request was issued with.
 * [onUnauthorized] receives null for requests that carried no credential.
 */
internal class NextDnsSessionExpiryInterceptor(
    private val onUnauthorized: (AccountCredentialRef?) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.requiresAuthentication()) onUnauthorized(request.accountRef())
        return response
    }

    private fun Response.requiresAuthentication(): Boolean {
        if (code == 401) return true
        if (code != 403) return false
        val root = runCatching {
            JsonParser.parseString(peekBody(MAX_ERROR_BODY_BYTES).string()).asJsonObject
        }.getOrNull() ?: return false
        return NextDnsErrorParser.parse(root).any { problem ->
            problem.code.equals("authRequired", ignoreCase = true)
        }
    }

    private companion object {
        const val MAX_ERROR_BODY_BYTES = 8_192L
    }
}
