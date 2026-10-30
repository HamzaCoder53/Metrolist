package io.github.hamzacoder53.wavelet.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.io.File
import java.util.TreeSet

/**
 * Least-recently-used evictor whose size limit can change at runtime (Media3's own LRU evictor
 * fixes the limit at construction, and a SimpleCache can't be reopened while in use).
 *
 * Whatever the limit (including "unlimited", [Long.MAX_VALUE]), it also keeps at least
 * [reserveBytes] free on the volume holding [storageDir], so the cache never fills the watch.
 */
@OptIn(UnstableApi::class)
class ResizableLruEvictor(
    maxBytes: Long,
    private val storageDir: File,
    private val reserveBytes: Long,
) : CacheEvictor {
    private val spans =
        TreeSet<CacheSpan>(
            compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it.key }.thenBy { it.position },
        )
    private var currentBytes = 0L

    @Volatile
    var maxBytes: Long = maxBytes
        private set

    @Synchronized
    fun resize(cache: Cache, newMaxBytes: Long) {
        maxBytes = newMaxBytes
        evict(cache, 0)
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    @Synchronized
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        currentBytes += span.length
        evict(cache, 0)
    }

    @Synchronized
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (spans.remove(span)) currentBytes -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    @Synchronized
    private fun evict(cache: Cache, requiredBytes: Long) {
        while (spans.isNotEmpty() && currentBytes + requiredBytes > limit()) {
            cache.removeSpan(spans.first())
        }
    }

    /** The configured limit, capped so the cache can only grow into free space above the reserve. */
    private fun limit(): Long {
        val roomOnDisk = currentBytes + (storageDir.usableSpace - reserveBytes).coerceAtLeast(0)
        return minOf(maxBytes, roomOnDisk)
    }
}
