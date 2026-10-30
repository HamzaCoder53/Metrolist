package io.github.hamzacoder53.wavelet.companion

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import io.github.hamzacoder53.wavelet.session.SessionCapture
import io.github.hamzacoder53.wavelet.session.SessionTransfer
import io.github.hamzacoder53.wavelet.session.YouTubeSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface Step {
    data object Start : Step

    data object SigningIn : Step

    data object Finishing : Step

    data object Sending : Step

    data class Done(val name: String?) : Step

    data class Failed(val message: String) : Step
}

/** Phone companion: sign in to YouTube Music here, then send the session to Wavelet on the watch. */
class SignInActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { CompanionScreen() }
            }
        }
    }
}

@Composable
private fun CompanionScreen() {
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    val capture = remember { SessionCapture() }
    var step by remember { mutableStateOf<Step>(Step.Start) }
    var watch by remember { mutableStateOf<Node?>(null) }
    var lookedForWatch by remember { mutableStateOf(false) }
    val failedCapture = stringResource(R.string.failed_capture)
    val failedSendTemplate = stringResource(R.string.failed_send, "%s")
    val failedWatchTemplate = stringResource(R.string.failed_watch, "%s")

    LaunchedEffect(Unit) {
        watch =
            runCatching {
                Wearable.getCapabilityClient(activity)
                    .getCapability(SessionTransfer.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                    .firstOrNull()
            }.getOrNull()
        lookedForWatch = true
    }

    fun send(session: YouTubeSession) {
        step = Step.Sending
        scope.launch {
            val target = watch
            if (target == null) {
                step = Step.Failed(failedSendTemplate.format("no watch connected"))
                return@launch
            }
            val messages = Wearable.getMessageClient(activity)
            val reply = CompletableDeferred<Pair<String, String>>()
            val listener =
                MessageClient.OnMessageReceivedListener { event ->
                    if (event.sourceNodeId == target.id && event.path.startsWith(SessionTransfer.PATH_SESSION + "/")) {
                        reply.complete(event.path to String(event.data))
                    }
                }
            messages.addListener(listener)
            try {
                messages.sendMessage(target.id, SessionTransfer.PATH_SESSION, session.toBytes()).await()
                val (path, body) =
                    withTimeoutOrNull(30_000) { reply.await() }
                        ?: (SessionTransfer.PATH_SESSION_FAILED to "the watch didn't answer")
                step =
                    if (path == SessionTransfer.PATH_SESSION_OK) {
                        Step.Done(body.ifBlank { null })
                    } else {
                        Step.Failed(failedWatchTemplate.format(body))
                    }
            } catch (e: Exception) {
                step = Step.Failed(failedSendTemplate.format(e.message ?: e.javaClass.simpleName))
            } finally {
                messages.removeListener(listener)
                // The phone keeps no copy of the login once it's been handed to the watch.
                CookieManager.getInstance().removeAllCookies(null)
            }
        }
    }

    fun onMusicPageLoaded(view: WebView) {
        if (step != Step.SigningIn) return
        step = Step.Finishing
        scope.launch {
            val session = capture.capture(view)
            if (session == null) step = Step.Failed(failedCapture) else send(session)
        }
    }

    if (step == Step.SigningIn || step == Step.Finishing) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> WebView(ctx).also { capture.attach(it, ::onMusicPageLoaded) } },
            )
            if (step == Step.Finishing) {
                Text(
                    stringResource(R.string.finishing),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
            TextButton(onClick = { step = Step.Start }, modifier = Modifier.align(Alignment.TopEnd)) {
                Text(stringResource(R.string.cancel))
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text =
                when {
                    !lookedForWatch -> stringResource(R.string.looking_for_watch)
                    watch == null -> stringResource(R.string.no_watch)
                    else -> stringResource(R.string.watch_found, watch!!.displayName)
                },
            textAlign = TextAlign.Center,
        )
        when (val current = step) {
            Step.Start -> {
                Text(stringResource(R.string.explainer), textAlign = TextAlign.Center)
                Button(onClick = { step = Step.SigningIn }, enabled = watch != null) {
                    Text(stringResource(R.string.sign_in))
                }
            }
            Step.Sending -> Text(stringResource(R.string.sending))
            is Step.Done ->
                Text(
                    current.name?.let { stringResource(R.string.done, it) } ?: stringResource(R.string.done_no_name),
                    textAlign = TextAlign.Center,
                )
            is Step.Failed -> {
                Text(current.message, textAlign = TextAlign.Center)
                Button(onClick = { step = Step.Start }) { Text(stringResource(R.string.try_again)) }
            }
            else -> Unit
        }
        Text(
            stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
