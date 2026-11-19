package com.eyalm.adns.data.nextdns.api

import com.eyalm.adns.data.account.AccountCredentialRef
import okhttp3.OkHttpClient

/**
 * Wires account-aware authentication into a client. This is the only place the interceptor
 * order is decided, and the tests build their clients with it, so they exercise the same chain
 * as the app.
 *
 * Order matters:
 * 1. Tagging runs first and records which credential the request was issued with.
 * 2. Session expiry reads that tag from the request it passed on, so a failure is charged to
 *    the issuing credential even if the active account has changed since.
 * 3. The key interceptor runs at the network layer, once per hop including redirects, and sends
 *    the key of the tagged credential only to [allowedHosts].
 */
internal fun OkHttpClient.Builder.addAccountAuthentication(
    currentRef: () -> AccountCredentialRef?,
    apiKeyFor: (AccountCredentialRef?) -> String?,
    onUnauthorized: (AccountCredentialRef?) -> Unit,
    allowedHosts: Set<String>,
): OkHttpClient.Builder = this
    .addInterceptor(AccountRequestTaggingInterceptor(currentRef))
    .addInterceptor(NextDnsSessionExpiryInterceptor(onUnauthorized))
    .addNetworkInterceptor(NextDnsApiKeyInterceptor(apiKeyFor, allowedHosts))
