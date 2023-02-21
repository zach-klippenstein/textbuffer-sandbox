package com.zachklipp.textbuffers.storage

import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord
import androidx.compose.runtime.snapshots.readable
import androidx.compose.runtime.snapshots.writable
import com.zachklipp.textbuffers.GetCharsTrait
import com.zachklipp.textbuffers.TextRange
import com.zachklipp.textbuffers.storage.ReplayingGapBufferStorage.GapBufferPool
import com.zachklipp.textbuffers.storage.ReplayingGapBufferStorage.ReplayingGapBuffer
import java.util.concurrent.atomic.AtomicReference

private const val DEBUG = false

/**
 * A snapshot-aware [TextBufferStorage] backed by a [ReplayingGapBuffer], which is in turn backed
 * by a [GapBufferStorageNoSnapshot]. New snapshot writes get a [ReplayingGapBuffer] from the
 * [GapBufferPool] and then [sync][ReplayingGapBuffer.syncTo] it to the buffer from the parent
 * snapshot. Buffers are returned the to pool when the record is re-assigned.
 *
 * This implementation is based on the approach described in
 * [this doc](https://docs.google.com/document/d/1gYewQYWKmlr_Dcwwn0PAiSCqHI85w7E-l_GrL2jlOdg/edit).
 * TODO copy that doc to a markdown file in this repo.
 */
class ReplayingGapBufferStorage(pool: GapBufferPool) : TextBufferStorage, StateObject {

    private var record = Record(pool)
    override val firstStateRecord: StateRecord get() = record

    override val length: Int
        get() = record.readable(this).readableBuffer().length

    override fun replace(range: TextRange, replacement: Char, sourceMark: Any?) {
        record.writable(this) {
            writableBuffer().replace(range, replacement, sourceMark)
        }
    }

    override fun <T> replace(
        range: TextRange,
        replacement: T,
        replacementRange: TextRange,
        sourceMark: Any?,
        getCharsTrait: GetCharsTrait<T>
    ) {
        record.writable(this) {
            writableBuffer().replace(
                range,
                replacement,
                replacementRange,
                sourceMark,
                getCharsTrait
            )
        }
    }

    @Suppress("ReplaceGetOrSet")
    override fun get(index: Int, sourceMark: Any?): Char =
        record.readable(this).readableBuffer().get(index, sourceMark)

    override fun getChars(
        srcBegin: Int,
        srcEnd: Int,
        dest: CharArray,
        destBegin: Int,
        sourceMark: Any?
    ) {
        record.readable(this).readableBuffer()
            .getChars(srcBegin, srcEnd, dest, destBegin, sourceMark)
    }

    override fun markRange(range: TextRange, newMark: Any, sourceMark: Any?) {
        record.writable(this) {
            writableBuffer().markRange(range, newMark, sourceMark)
        }
    }

    override fun unmark(mark: Any) {
        record.writable(this) {
            writableBuffer().unmark(mark)
        }
    }

    override fun getRangeForMark(mark: Any, sourceMark: Any?): TextRange =
        record.readable(this).readableBuffer().getRangeForMark(mark, sourceMark)

    override fun <R : Any> getMarksIntersecting(
        range: TextRange,
        sourceMark: Any?,
        predicate: (Any, TextRange) -> R?
    ): List<R> = record.writable(this) {
        writableBuffer().getMarksIntersecting(range, sourceMark, predicate)
    }

    override fun prependStateRecord(value: StateRecord) {
        record = value as Record
    }

    override fun toString(): String = "ReplayingGapBufferStorage(\"${contentsToString()}\")"

    private class Record(private val pool: GapBufferPool) : StateRecord() {
        private var buffer: ReplayingGapBuffer? = null
        private var hasCopiedForWrite = false

        fun readableBuffer(): ReplayingGapBuffer =
            buffer ?: pool.getBufferForCapacity(16).also {
                buffer = it
                hasCopiedForWrite = true
            }

        fun writableBuffer(): ReplayingGapBuffer = when {
            buffer == null -> readableBuffer()
            hasCopiedForWrite -> buffer!!
            else -> {
                pool.getBufferForCapacity(buffer!!.length)
                    .also { newBuffer ->
                        newBuffer.syncTo(buffer!!)
                        buffer = newBuffer
                        hasCopiedForWrite = true
                    }
            }
        }

        override fun assign(value: StateRecord) {
            buffer?.let(pool::recycleBuffer)
            buffer = (value as Record).buffer
            hasCopiedForWrite = false
        }

        override fun create(): StateRecord = Record(pool)
    }

    interface GapBufferPool {
        fun getBufferForCapacity(capacity: Int): ReplayingGapBuffer
        fun recycleBuffer(buffer: ReplayingGapBuffer)

        companion object {
            val Unpooled = object : GapBufferPool {
                override fun getBufferForCapacity(capacity: Int) = ReplayingGapBuffer(capacity)
                override fun recycleBuffer(buffer: ReplayingGapBuffer) {}
            }

            fun singleBuffer() = object : GapBufferPool {
                private var cache = AtomicReference<ReplayingGapBuffer>()

                override fun getBufferForCapacity(capacity: Int): ReplayingGapBuffer {
                    val builder = cache.get()
                    return if (builder != null && cache.compareAndSet(builder, null)) {
                        builder
                    } else {
                        ReplayingGapBuffer(capacity)
                    }
                }

                override fun recycleBuffer(buffer: ReplayingGapBuffer) {
                    // Don't clear - want to be able to replay!
                    cache.compareAndSet(null, buffer)
                }
            }
        }
    }

    /**
     * A [TextBufferStorage] that is backed by a [GapBufferStorageNoSnapshot]. A buffer can be "synced"
     * to a source buffer via [syncTo]. Syncing will replace the entire contents of this buffer with the
     * source buffer by default. However, if the source buffer was previously synced to _this_ buffer,
     * and only simple operations were performed on it since, then this buffer will simply replay the
     * operations on itself. This is often cheaper than replacing the entire contents since it takes
     * advantage of the underlying gap buffer.
     *
     * This is effectively a very basic conflict-free replicated data type (CRDT).
     */
    class ReplayingGapBuffer(initialCapacity: Int = 32) : TextBufferStorage {

        private val realBuffer = GapBufferStorageNoSnapshot(initialCapacity)

        private var source: ReplayingGapBuffer? = null
        private var diffSource: TextRange = TextRange.Unspecified
        private var diffResult: TextRange = TextRange.Unspecified

        /**
         * If this is true and [diffSource] is unspecified, then we haven't recorded any changes as
         * diffs yet. If this is false, then we recorded a diff at some point, but then a later
         * change invalidated it.
         */
        private var diffValid = true

        private var diffCount = 0

        override val length: Int
            get() = realBuffer.length

        // TODO add support for syncing > 1 buffer deep
        fun syncTo(source: ReplayingGapBuffer) {
            this.source = source
            this.diffCount = 0
            if (source.source === this &&
                source.diffValid &&
                source.diffSource != TextRange.Unspecified
            ) {
                // Replay diff
                if (DEBUG) println("syncTo replaying ${source.diffCount} diffs")
                replace(
                    range = source.diffSource,
                    replacement = source,
                    replacementRange = source.diffResult,
                    getCharsTrait = TextBufferStorage
                )
            } else {
                // Full sync
                if (DEBUG) println("syncTo doing full sync")
                replace(
                    replacement = source,
                    replacementRange = TextRange(0, source.length),
                    getCharsTrait = TextBufferStorage
                )
            }
            // Reset any diff recorded by the above replace commands.
            diffSource = TextRange.Unspecified
            diffValid = true
            this.diffCount = 0
        }

        override fun <T> replace(
            range: TextRange,
            replacement: T,
            replacementRange: TextRange,
            sourceMark: Any?,
            getCharsTrait: GetCharsTrait<T>
        ) {
            realBuffer.replace(range, replacement, replacementRange, sourceMark, getCharsTrait)
            // If we already gave up trying to record a diff, don't try again until the next sync.
            if (!diffValid) return
            if (diffSource == TextRange.Unspecified) {
                // This is the first change since a sync or instantiation - start recording changes.
                diffSource = range
                diffResult = TextRange(
                    range.startInclusive,
                    range.startInclusive + replacementRange.length
                )
                diffCount++
            } else if (range.startInclusive == diffResult.endExclusive) {
                // New replacement text right after the end of the last one (aka append), so we can
                // merge them.
                diffSource = TextRange(
                    // The start stays the same since this came _after_ it.
                    diffSource.startInclusive,
                    // We need the new end of the source range, but we need to adjust it to account
                    // for the previous diff.
                    range.endExclusive - diffResult.length + diffSource.length
                )
                diffResult = TextRange(
                    diffResult.startInclusive,
                    range.startInclusive + replacementRange.length
                )
                diffCount++
            } else if (range.endExclusive == diffResult.startInclusive) {
                // Prepend
                diffSource = TextRange(
                    range.startInclusive,
                    diffSource.endExclusive
                )
                diffResult = TextRange(
                    range.startInclusive,
                    // Account for the result being offset by the previous diff.
                    diffResult.endExclusive + replacementRange.length - range.length
                )
                diffCount++
            } else {
                // Changes were too complicated, can't represent diff.
                // TODO add support for >1 diff
                diffSource = TextRange.Unspecified
                diffResult = TextRange.Unspecified
                diffValid = false
            }
            if (DEBUG) println("diffCount=$diffCount, valid=$diffValid")
        }

        @Suppress("ReplaceGetOrSet")
        override fun get(index: Int, sourceMark: Any?): Char = realBuffer.get(index, sourceMark)

        override fun getChars(
            srcBegin: Int,
            srcEnd: Int,
            dest: CharArray,
            destBegin: Int,
            sourceMark: Any?
        ) = realBuffer.getChars(srcBegin, srcEnd, dest, destBegin, sourceMark)

        override fun markRange(range: TextRange, newMark: Any, sourceMark: Any?) {
            realBuffer.markRange(range, newMark, sourceMark)
        }

        override fun unmark(mark: Any) {
            realBuffer.unmark(mark)
        }

        override fun getRangeForMark(mark: Any, sourceMark: Any?): TextRange =
            realBuffer.getRangeForMark(mark, sourceMark)

        override fun <R : Any> getMarksIntersecting(
            range: TextRange,
            sourceMark: Any?,
            predicate: (Any, TextRange) -> R?
        ): List<R> = realBuffer.getMarksIntersecting(range, sourceMark, predicate)
    }
}