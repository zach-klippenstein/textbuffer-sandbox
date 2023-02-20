package com.zachklipp.textbuffers.storage

import com.zachklipp.textbuffers.GetCharsTrait
import com.zachklipp.textbuffers.TextRange

/**
 * Storage implementation for [TextBuffer].
 *
 * In a KMP library this would be pure common code.
 */
interface TextBufferStorage {
    val length: Int

    fun replace(
        range: TextRange = TextRange.Unspecified,
        replacement: Char,
        sourceMark: Any? = null
    ) = replace(range, replacement, TextRange(0, 1), sourceMark, SingleCharGetCharsTrait)

    /**
     * Replace the characters in this buffer in [range] with the characters in [replacementRange]
     * from [replacement]. If [sourceMark] is non-null, [range] is relative to the mark.
     */
    fun <T> replace(
        range: TextRange = TextRange.Unspecified,
        replacement: T,
        replacementRange: TextRange,
        sourceMark: Any? = null,
        getCharsTrait: GetCharsTrait<T>
    )

    operator fun get(index: Int, sourceMark: Any? = null): Char

    /**
     * Copy the characters from the buffer from [srcBegin] to [srcEnd] to [dest] starting at
     * [destBegin]. If [sourceMark] is non-null, [srcBegin] and [srcEnd] are relative to the mark.
     */
    fun getChars(
        srcBegin: Int,
        srcEnd: Int,
        dest: CharArray,
        destBegin: Int,
        sourceMark: Any? = null
    )

    /**
     * Starts tracking the specified [range]. When the text is changed, the range associated with
     * the mark will be adjusted (e.g. text inserted before the range will increase the indices,
     * text inserted inside the range will increase the range's length). [getRangeForMark] can be
     * used to get the updated range after the edits.
     */
    fun markRange(range: TextRange, newMark: Any, sourceMark: Any? = null)

    /**
     * Removes a mark previously set by [markRange].
     */
    fun unmark(mark: Any)

    /**
     * Gets the current range for the [mark] previously passed into [markRange]. If [sourceMark] is
     * non-null, the returned range will be relative to it.
     */
    fun getRangeForMark(mark: Any, sourceMark: Any? = null): TextRange

    /**
     * Gets all marks previously passed into [markRange] whose current ranges intersect [range].
     * If [sourceMark] is non-null, [range] is relative to the mark.
     */
    fun <R : Any> getMarksIntersecting(
        range: TextRange,
        sourceMark: Any? = null,
        predicate: (Any, TextRange) -> R?
    ): List<R>

    companion object : GetCharsTrait<TextBufferStorage> {
        override fun getChars(
            src: TextBufferStorage,
            srcBegin: Int,
            srcEnd: Int,
            dest: CharArray,
            destBegin: Int
        ) = src.getChars(srcBegin, srcEnd, dest, destBegin)
    }
}

fun TextBufferStorage.contentsToString(
    range: TextRange = TextRange.Unspecified,
    mark: Any? = null
): String {
    val start = range.startInclusive.takeUnless { it == -1 } ?: 0
    val end = range.endExclusive.takeUnless { it == -1 } ?: length
    require(start >= 0 && end <= length) { "Expected range to be in 0..$length but was [$start, $end)" }
    val chars = CharArray(end - start)
    getChars(start, end, chars, 0, mark)
    return String(chars)
}

private object SingleCharGetCharsTrait : GetCharsTrait<Char> {
    override fun getChars(src: Char, srcBegin: Int, srcEnd: Int, dest: CharArray, destBegin: Int) {
        require(srcBegin == 0 && srcEnd == 1)
        dest[destBegin] = src
    }
}