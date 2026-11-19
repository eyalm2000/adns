package com.eyalm.adns.data.nextdns.api

import com.eyalm.adns.data.account.AccountCredentialRef
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stamps each request with the credential that was current when it was issued. Later stages
 * of the chain use the stamp to pick the API key and to attribute an authentication failure
 * to the right account, even if the active account changes while the request is in flight.
 *
 * Must run before [NextDnsSessionExpiryInterceptor].
 */
internal class AccountRequestTaggingInterceptor(
    private val currentRef: () -> AccountCredentialRef?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val ref = currentRef() ?: return chain.proceed(chain.request())
        return chain.proceed(
            chain.request().newBuilder()
                .tag(AccountCredentialRef::class.java, ref)
                .build()
        )
    }
}
