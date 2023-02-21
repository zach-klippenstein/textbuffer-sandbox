package com.zachklipp.textbuffers.storage

import com.zachklipp.textbuffers.GetCharsTrait
import com.zachklipp.textbuffers.TextRange

private const val DEBUG = false

/**
 * A simple gap buffer-backed [TextBufferStorage] that does not support snapshots.
 *
 * @param initialCapacity The initial size of the underlying array.
 * @param minimumGapLength When a replace operation increases the size of the buffer, if the new gap
 * size will be less than this value, a new, larger underlying array will be allocated.
 */
class GapBufferStorageNoSnapshot(
    initialCapacity: Int = 32,
    private val minimumGapLength: Int = 8
) : TextBufferStorage {

    private var buffer: CharArray = CharArray(initialCapacity + minimumGapLength * 2)
    private var gapStart: Int = 0
    private var gapEnd: Int = buffer.size
    private val gapLen get() = gapEnd - gapStart

    override val length: Int
        get() = buffer.size - gapLen

    override fun <T> replace(
        range: TextRange,
        replacement: T,
        replacementRange: TextRange,
        sourceMark: Any?,
        getCharsTrait: GetCharsTrait<T>
    ) {
        requireNoMark(sourceMark)
        checkRange(range, length)
        val start = range.startInclusive.takeUnless { it == -1 } ?: 0
        val end = range.endExclusive.takeUnless { it == -1 } ?: length
        val srcLen = end - start
        val destLen = replacementRange.length

        // Replacing nothing with nothing is a noop.
        if (srcLen == 0 && destLen == 0) return

        if (DEBUG) println("replacing [${start},${end}) in \"${toStringWithGap()}\" with \"$replacement\"")

        // If the buffer is too small to hold the new text, re-allocate.
        val newGapLen = gapLen + srcLen - destLen
        if (newGapLen < minimumGapLength) {
            if (DEBUG) println(" not enough gap, reallocating ($newGapLen < $minimumGapLength)")
            val newBuffer = CharArray(buffer.size * 2)
            // Copy new before-gap.
            getChars(0, start, newBuffer, 0)
            // Copy new after-gap.
            val secondHalfSize = length - end
            val newGapEnd = newBuffer.size - secondHalfSize
            getChars(end, length, newBuffer, newGapEnd)
            buffer = newBuffer
            gapStart = start
            gapEnd = newGapEnd
            if (DEBUG) println("newly allocated buffer: \"${toStringWithGap()}\"")
        } else {
            // Ensure the gap is adjacent to the range to replace.
            // Note that we could collapse these two cases into one and it would still be correct,
            // but if the gap is before the insertion point it would copy more elements than
            // necessary.
            if (gapStart < start) {
                // Move the gap forwards so the end of the gap is right before the start of the
                // range to replace.
                val moveLen = start - gapStart
                if (DEBUG) println(" gap is before range, moving $moveLen chars from $gapEnd to $gapStart")
                buffer.copyInto(
                    destination = buffer,
                    destinationOffset = gapStart,
                    startIndex = gapEnd,
                    endIndex = gapEnd + moveLen
                )
                gapStart += moveLen
                gapEnd += moveLen

                // Gap is now adjacent to range to replace, expand the gap to remove the necessary
                // characters.
                if (DEBUG) println(" gapStart=$gapStart, gapEnd=$gapEnd, growing gap forwards by $srcLen")
                gapEnd += srcLen
                if (DEBUG) println(" gapStart=$gapStart, gapEnd=$gapEnd: \"${toStringWithGap()}\"")
            } else if (gapStart > end) {
                // Move the gap backwards so the start of the gap is right after the end of the
                // range to replace.
                val moveLen = gapStart - end
                if (DEBUG) println(" gap is after range, moving $moveLen chars from $start to ${gapEnd - moveLen}")
                buffer.copyInto(
                    destination = buffer,
                    destinationOffset = gapEnd - moveLen,
                    startIndex = end,
                    endIndex = end + moveLen
                )
                gapStart -= moveLen
                gapEnd -= moveLen

                // Gap is now adjacent to range to replace, expand the gap to remove the necessary
                // characters.
                if (DEBUG) println(" gapStart=$gapStart, gapEnd=$gapEnd, growing gap backwards by $srcLen")
                gapStart -= srcLen
                if (DEBUG) println(" gapStart=$gapStart, gapEnd=$gapEnd: \"${toStringWithGap()}\"")
            } else {
                // Gap is somewhere in the middle of the range to replace, we don't need to move
                // the gap, but we do need to grow it in one or both directions.
                val growBack = gapStart - start
                val growForward = srcLen - growBack
                if (DEBUG) println(" gapStart=$gapStart, gapEnd=$gapEnd, growing gap backwards by $growBack and forwards by $growForward")
                gapStart -= growBack
                gapEnd += growForward
                if (DEBUG) println(" gapStart=$gapStart, gapEnd=$gapEnd: \"${toStringWithGap()}\"")
            }
        }

        // Gap is now in the right place and the gap start/end have been updated to remove the old
        // text.
        getCharsTrait.getChars(
            src = replacement,
            srcBegin = replacementRange.startInclusive,
            srcEnd = replacementRange.endExclusive,
            dest = buffer,
            destBegin = gapStart
        )
        gapStart += destLen
        if (DEBUG) println("finished replace: \"${toStringWithGap()}\"")
    }

    override fun get(index: Int, sourceMark: Any?): Char {
        requireNoMark(sourceMark)
        checkRange(TextRange(index), length - 1)

        val bufferIndex = if (index < gapStart) index else index + gapLen
        return buffer[bufferIndex]
    }

    override fun getChars(
        srcBegin: Int,
        srcEnd: Int,
        dest: CharArray,
        destBegin: Int,
        sourceMark: Any?
    ) {
        requireNoMark(sourceMark)
        checkRange(TextRange(srcBegin, srcEnd), length)
        val getLength = srcEnd - srcBegin
        require(destBegin in 0..(dest.size - getLength)) {
            "Expected destBegin to be in 0..${dest.size - getLength} but was $destBegin"
        }
        if (DEBUG) println("getChars(srcBegin=$srcBegin, srcEnd=$srcEnd, destBegin=$destBegin) gapStart=$gapStart, gapEnd=$gapEnd")

        when {
            // Empty fetch, nothing to do.
            srcBegin == srcEnd -> {
                if (DEBUG) println(" empty source range, nothing to do")
                return
            }
            // Entire section is before gap, single copy.
            srcEnd <= gapStart -> {
                if (DEBUG) println(" source range is before gap")
                buffer.copyInto(
                    destination = dest,
                    destinationOffset = destBegin,
                    startIndex = srcBegin,
                    endIndex = srcEnd
                )
            }
            // Entire section is after gap, single copy.
            srcBegin >= gapStart -> {
                val copyStart = srcBegin + gapLen
                if (DEBUG) println(" source range is after gap. Copying from [$copyStart,${copyStart + getLength}) to $destBegin")
                buffer.copyInto(
                    destination = dest,
                    destinationOffset = destBegin,
                    startIndex = copyStart,
                    endIndex = copyStart + getLength
                )
            }
            // Fetch covers gap, two copies.
            else -> {
                if (DEBUG) println(" source range covers gap")
                val firstHalfLength = gapStart - srcBegin
                if (DEBUG) println(" copying 1st half from [${srcBegin},${srcBegin + firstHalfLength}) to $destBegin")
                buffer.copyInto(
                    destination = dest,
                    destinationOffset = destBegin,
                    startIndex = srcBegin,
                    endIndex = srcBegin + firstHalfLength
                )
                val secondHalfLength = getLength - firstHalfLength
                if (DEBUG) println(" copying 2nd half from [${gapEnd},${gapEnd + secondHalfLength}) to ${destBegin + firstHalfLength}")
                buffer.copyInto(
                    destination = dest,
                    destinationOffset = destBegin + firstHalfLength,
                    startIndex = gapEnd,
                    endIndex = gapEnd + secondHalfLength
                )
            }
        }
    }

    override fun markRange(range: TextRange, newMark: Any, sourceMark: Any?) {
        TODO("Not yet implemented")
    }

    override fun unmark(mark: Any) {
        TODO("Not yet implemented")
    }

    override fun getRangeForMark(mark: Any, sourceMark: Any?): TextRange {
        TODO("Not yet implemented")
    }

    override fun <R : Any> getMarksIntersecting(
        range: TextRange,
        sourceMark: Any?,
        predicate: (Any, TextRange) -> R?
    ): List<R> {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "GapBufferStorageNoSnapshot(\"${contentsToString()}\")"

    private fun requireNoMark(mark: Any?) {
        require(mark == null) { "Marking not supported" }
    }

    private fun checkRange(range: TextRange, startMax: Int) {
        val start = range.startInclusive
        val end = range.endExclusive
        require(
            start <= end &&
                    (start == -1 || start in 0..startMax) &&
                    (end == -1 || end in 0..length)
        ) { "Expected range to be in 0..$startMax but was $range" }
    }

    private fun toStringWithGap(): String = buildString {
        append(contentsToString(TextRange(0, gapStart)))
        append('[')
        repeat(gapLen) { append('_') }
        append(']')
        if (gapStart < this@GapBufferStorageNoSnapshot.length) {
            append(contentsToString(TextRange(gapStart, this@GapBufferStorageNoSnapshot.length)))
        }
    }
}