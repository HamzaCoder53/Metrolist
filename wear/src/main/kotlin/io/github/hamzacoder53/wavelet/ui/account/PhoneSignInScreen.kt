package io.github.hamzacoder53.wavelet.ui.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.account.AccountMode
import io.github.hamzacoder53.wavelet.session.SessionTransfer
import io.github.hamzacoder53.wavelet.ui.CenteredText
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * "Sign in on phone": opens the Wavelet companion on the paired phone, then waits here until the
 * phone sends the session (handled by [io.github.hamzacoder53.wavelet.account.SessionReceiverService]).
 */
@Composable
fun PhoneSignInScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WaveletApplication
    val state by app.account.state.collectAsState()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(R.string.phone_sign_in_opening) }
    val startState = remember { state }

    fun openOnPhone() {
        scope.launch {
            status =
                runCatching {
                    val phones =
                        Wearable.getCapabilityClient(context)
                            .getCapability(SessionTransfer.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                            .await()
                            .nodes
                    val helper = RemoteActivityHelper(context)
                    if (phones.isEmpty()) {
                        // Companion not installed (or phone not connected): open its Play listing on the phone.
                        helper.startRemoteActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                                .addCategory(Intent.CATEGORY_BROWSABLE),
                        ).await()
                        R.string.phone_sign_in_install
                    } else {
                        helper.startRemoteActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(SessionTransfer.SIGN_IN_URI))
                                .addCategory(Intent.CATEGORY_BROWSABLE),
                            phones.first().id,
                        ).await()
                        R.string.phone_sign_in_continue
                    }
                }.getOrElse {
                    Timber.w(it, "Couldn't open Wavelet on the phone")
                    R.string.phone_sign_in_no_phone
                }
        }
    }

    LaunchedEffect(Unit) { openOnPhone() }
    // Done once a new (non-expired) web session arrives from the phone.
    LaunchedEffect(state) {
        if (state.mode == AccountMode.WEB && !state.expired && state != startState) {
            app.accountLibrary.sync()
            onDone()
        }
    }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.sign_in_on_phone)) } }
            item { CenteredText(stringResource(status)) }
            item {
                FilledTonalButton(
                    onClick = ::openOnPhone,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.open_on_phone_again)) },
                )
            }
        }
    }
}
