package com.eyalm.adns.data.network

import android.content.Context
import com.eyalm.adns.data.TokenManager
import com.eyalm.adns.data.nextdns.api.addAccountAuthentication
import com.eyalm.adns.data.nextdns.api.NextDnsApi
import com.eyalm.adns.data.nextdns.api.NextDnsApiKeyAuthApi
import com.eyalm.adns.data.nextdns.api.NextDnsCookieAuthApi
import com.eyalm.adns.data.nextdns.api.NextDnsIpv4Api
import com.eyalm.adns.data.nextdns.api.NextDnsLinkIpApi
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager
import com.eyalm.adns.data.nextdns.connection.NextDnsConnectionApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val API_BASE_URL = "https://api.nextdns.io/"
    private const val API_HOST = "api.nextdns.io"
    private const val IPV4_API_BASE_URL = "https://ipv4.api.nextdns.io/"
    private const val IPV4_API_HOST = "ipv4.api.nextdns.io"
    private const val LINK_IP_BASE_URL = "https://link-ip.nextdns.io/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/148.0.0.0 Safari/537.36"

    private lateinit var nextDnsApiInternal: NextDnsApi
    private lateinit var nextDnsIpv4ApiInternal: NextDnsIpv4Api
    private lateinit var nextDnsCookieAuthApiInternal: NextDnsCookieAuthApi
    private lateinit var nextDnsApiKeyAuthApiInternal: NextDnsApiKeyAuthApi
    private lateinit var nextDnsLinkIpApiInternal: NextDnsLinkIpApi
    private lateinit var nextDnsConnectionApiInternal: NextDnsConnectionApi

    /**
     * Initialize the API client. Call this once from Application.onCreate() or similar with an app Context.
     */
    @Synchronized
    fun init(context: Context) {
        if (::nextDnsApiInternal.isInitialized) return

        val tokenManager = TokenManager.getInstance(context.applicationContext)
        val sessionManager = NextDnsSessionManager.getInstance(context.applicationContext)

        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 30
            maxRequestsPerHost = 15
        }

        val baseClient = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val request = originalRequest.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val authenticatedClient = baseClient.newBuilder()
            .addAccountAuthentication(
                currentRef = tokenManager::credentialRef,
                apiKeyFor = tokenManager::apiKeyFor,
                onUnauthorized = sessionManager::unauthorized,
                allowedHosts = setOf(API_HOST, IPV4_API_HOST),
            )
            .build()

        val cookieAuthClient = baseClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Origin", "https://my.nextdns.io")
                    .header("Referer", "https://my.nextdns.io/")
                    .build()
                chain.proceed(request)
            }
            .build()

        nextDnsApiInternal = retrofit(API_BASE_URL, authenticatedClient)
            .create(NextDnsApi::class.java)
        nextDnsIpv4ApiInternal = retrofit(IPV4_API_BASE_URL, authenticatedClient)
            .create(NextDnsIpv4Api::class.java)
        nextDnsCookieAuthApiInternal = retrofit(API_BASE_URL, cookieAuthClient)
            .create(NextDnsCookieAuthApi::class.java)
        nextDnsApiKeyAuthApiInternal = retrofit(API_BASE_URL, baseClient)
            .create(NextDnsApiKeyAuthApi::class.java)
        nextDnsLinkIpApiInternal = retrofit(LINK_IP_BASE_URL, baseClient)
            .create(NextDnsLinkIpApi::class.java)
        nextDnsConnectionApiInternal = retrofit(API_BASE_URL, baseClient)
            .create(NextDnsConnectionApi::class.java)
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val nextDnsApi: NextDnsApi
        get() = initialized { nextDnsApiInternal }

    val nextDnsIpv4Api: NextDnsIpv4Api
        get() = initialized { nextDnsIpv4ApiInternal }

    val nextDnsCookieAuthApi: NextDnsCookieAuthApi
        get() = initialized { nextDnsCookieAuthApiInternal }

    val nextDnsApiKeyAuthApi: NextDnsApiKeyAuthApi
        get() = initialized { nextDnsApiKeyAuthApiInternal }

    val nextDnsLinkIpApi: NextDnsLinkIpApi
        get() = initialized { nextDnsLinkIpApiInternal }

    val nextDnsConnectionApi: NextDnsConnectionApi
        get() = initialized { nextDnsConnectionApiInternal }

    private fun <T> initialized(api: () -> T): T {
        if (!::nextDnsApiInternal.isInitialized) {
            throw IllegalStateException(
                "ApiClient not initialized. Call ApiClient.init(context) before using it."
            )
        }
        return api()
    }
}
