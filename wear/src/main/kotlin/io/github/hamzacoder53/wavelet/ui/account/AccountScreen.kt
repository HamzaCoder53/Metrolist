package io.github.hamzacoder53.wavelet.ui.account

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.WaveletApplication
import io.github.hamzacoder53.wavelet.ui.CenteredText
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    onPhoneSignIn: () -> Unit,
    onWebSignIn: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as WaveletApplication
    val account = app.account
    val state by account.state.collectAsState()
    val snapshot by app.accountLibrary.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.account)) } }
            if (state.expired) {
                // The saved sign-in stopped working: say so plainly and offer the matching way back in.
                item { CenteredText(stringResource(R.string.sign_in_expired)) }
                item {
                    Button(
                        onClick = onPhoneSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sign_in_again)) },
                    )
                }
            }
            if (!state.signedIn) {
                item { CenteredText(stringResource(R.string.sign_in_explainer)) }
                item {
                    Button(
                        onClick = onPhoneSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_phone), contentDescription = null) },
                        label = { Text(stringResource(R.string.sign_in_on_phone)) },
                        secondaryLabel = { Text(stringResource(R.string.sign_in_on_phone_detail)) },
                    )
                }
                if (account.webSignInAvailable) {
                    item {
                        FilledTonalButton(
                            onClick = onWebSignIn,
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null) },
                            label = { Text(stringResource(R.string.sign_in_on_watch)) },
                            secondaryLabel = { Text(stringResource(R.string.sign_in_on_watch_detail)) },
                        )
                    }
                }
            } else {
                item {
                    CenteredText(
                        state.name ?: stringResource(R.string.signed_in_full),
                    )
                }
                item {
                    Button(
                        onClick = onOpenLibrary,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(painterResource(R.drawable.ic_library), contentDescription = null) },
                        label = { Text(stringResource(R.string.your_library)) },
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = {
                            syncing = true
                            scope.launch {
                                app.accountLibrary.sync()
                                syncing = false
                            }
                        },
                        enabled = !syncing,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(if (syncing) R.string.syncing else R.string.sync_now)) },
                        secondaryLabel = {
                            if (snapshot.syncedAt > 0) {
                                Text(DateUtils.getRelativeTimeSpanString(snapshot.syncedAt).toString())
                            }
                        },
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = { confirmSignOut = true },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sign_out)) },
                    )
                }
            }
        }
    }

    AlertDialog(
        visible = confirmSignOut,
        onDismissRequest = { confirmSignOut = false },
        title = { Text(stringResource(R.string.sign_out_title)) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    account.signOut()
                    app.accountLibrary.clear()
                    confirmSignOut = false
                },
            )
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = { confirmSignOut = false }) },
    )
}
