package com.zachklipp.textbuffers

import kotlin.reflect.KClass

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
        mark: Any? = null
    )

    /**
     * Replace the characters in this buffer in [range] with the characters in [replacementRange]
     * from [replacement]. If [mark] is non-null, [range] is relative to the mark.
     */
    context(GetCharsTrait<T>)
    fun <T> replace(
        range: TextRange = TextRange.Unspecified,
        replacement: T,
        replacementRange: TextRange,
        mark: Any? = null
    )

    operator fun get(index: Int, mark: Any? = null): Char

    /**
     * Copy the characters from the buffer from [srcBegin] to [srcEnd] to [dest] starting at
     * [destBegin]. If [mark] is non-null, [srcBegin] and [srcEnd] are relative to the mark.
     */
    fun getChars(srcBegin: Int, srcEnd: Int, dest: CharArray, destBegin: Int, mark: Any? = null)

    /**
     * Starts tracking the specified [range]. When the text is changed, the range associated with
     * the mark will be adjusted (e.g. text inserted before the range will increase the indices,
     * text inserted inside the range will increase the range's length). [getRangeForMark] can be
     * used to get the updated range after the edits.
     */
    fun markRange(range: TextRange, newMark: Any, sourceMark: Any? = null)

    /**
     * Gets the current range for the [mark] previously passed into [markRange]. If [sourceMark] is
     * non-null, the returned range will be relative to it.
     */
    fun getRangeForMark(mark: Any, sourceMark: Any? = null): TextRange

    /**
     * Gets all marks previously passed into [markRange] whose current ranges intersect [range].
     * If [mark] is non-null, [range] is relative to the mark.
     */
    fun <T : Any> getMarksIntersecting(
        range: TextRange,
        type: KClass<T>,
        mark: Any? = null
    ): List<T>

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
    val start = range.startInclusive
    val end = range.endExclusive
    val chars = CharArray(end - start)
    getChars(start, end, chars, 0, mark)
    return String(chars)
}