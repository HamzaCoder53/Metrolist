package io.github.hamzacoder53.wavelet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import io.github.hamzacoder53.wavelet.R
import io.github.hamzacoder53.wavelet.playback.THUMBNAIL_PX
import io.github.hamzacoder53.wavelet.playback.sizedThumbnail

@Composable
fun QueueScreen(player: PlayerViewModel) {
    val queue by player.queue.collectAsState()
    var pendingRemove by remember { mutableStateOf<QueueEntry?>(null) }
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.up_next)) } }

            if (queue.isEmpty()) {
                item { CenteredText(stringResource(R.string.queue_empty)) }
            } else {
                itemsIndexed(queue, key = { index, _ -> index }) { _, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { player.playQueueIndex(item.timelineIndex) },
                            onLongClick = { pendingRemove = item },
                            modifier = Modifier.weight(1f),
                            icon = {
                                AsyncImage(
                                    model = item.thumbnail?.sizedThumbnail(THUMBNAIL_PX),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                )
                            },
                            label = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = {
                                Text(
                                    if (item.isCurrent) stringResource(R.string.now_playing) else item.artist.orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                        if (!item.isCurrent) {
                            IconButton(
                                onClick = { player.removeFromQueue(item.timelineIndex) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_remove),
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        visible = pendingRemove != null,
        onDismissRequest = { pendingRemove = null },
        title = { Text(stringResource(R.string.remove_from_queue)) },
        text = {
            pendingRemove?.let {
                Text(it.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    pendingRemove?.let { player.removeFromQueue(it.timelineIndex) }
                    pendingRemove = null
                },
            )
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = { pendingRemove = null })
        },
    )
}
