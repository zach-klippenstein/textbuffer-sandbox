package com.zachklipp.textbuffers.storage

import com.zachklipp.textbuffers.GetCharsTrait
import com.zachklipp.textbuffers.TextRange

internal fun StringBuilder.replace(
    range: TextRange,
    replacement: Char,
    sourceMark: Any?
) {
    @Suppress("NAME_SHADOWING")
    val range = if (range == TextRange.Unspecified) TextRange(0, length) else range
    if (range.length == 0) {
        insert(range.startInclusive, replacement)
    } else {
        replace(range.startInclusive, range.endExclusive, replacement.toString())
    }
}

context(GetCharsTrait<T>)
internal fun <T> StringBuilder.replace(
    range: TextRange,
    replacement: T,
    replacementRange: TextRange,
    sourceMark: Any?,
) {
    @Suppress("NAME_SHADOWING")
    val range = if (range == TextRange.Unspecified) TextRange(0, length) else range

    if (replacementRange.length == 0) {
        replace(range.startInclusive, range.endExclusive, "")
    } else {
        val chars = CharArray(replacementRange.length)
        getChars(
            replacement,
            replacementRange.startInclusive,
            replacementRange.endExclusive,
            chars,
            0
        )
        val replacementString = String(chars)

        if (range.length == 0) {
            insert(range.startInclusive, replacementString)
        } else {
            replace(range.startInclusive, range.endExclusive, replacementString)
        }
    }
}