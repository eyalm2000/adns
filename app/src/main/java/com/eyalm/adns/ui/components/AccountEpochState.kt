package com.eyalm.adns.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.eyalm.adns.data.account.AccountEpoch
import com.eyalm.adns.data.nextdns.auth.NextDnsSessionManager

/**
 * The current account epoch. Screens that load NextDNS data include its [AccountEpoch.sequence]
 * in the key of their ViewModel, so switching accounts gives them a new ViewModel instead of
 * keeping the previous account's loaded data. A request still running in the old instance can
 * only change that instance, which nothing displays any more.
 */
@Composable
fun rememberAccountEpoch(): AccountEpoch {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { NextDnsSessionManager.getInstance(context) }
    val epoch by manager.accountEpoch.collectAsState()
    return epoch
}
