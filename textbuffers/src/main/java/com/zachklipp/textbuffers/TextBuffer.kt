package com.zachklipp.textbuffers

import com.zachklipp.textbuffers.storage.TextBufferStorage
import com.zachklipp.textbuffers.storage.contentsToString
import java.text.CharacterIterator

/**
 * A mutable text buffer that is backed by a [TextBufferStorage].
 *
 * This class is not covered by tests or benchmarks in this project. It's purpose is to serve as
 * an API usage test of [TextBufferStorage], to see how its APIs feel to implement higher-level
 * things.
 *
 * This is basically just a wrapper for [TextBufferStorage] that provides more convenient public
 * APIs. In a KMP library, this class would be expect/actual and implement all the relevant
 * platform-specific text-related types.
 *
 * It also makes use of the [TextBufferStorage.markRange] API to implement two higher-level
 * features:
 *  - Creating "slices" of the buffer that track the originally-sliced range ([slice]).
 *  - A simple string-based "annotation" system ([setAnnotation], [getAnnotations],
 *   [removeAnnotations]).
 */
open class TextBuffer private constructor(
    private val storage: TextBufferStorage
) : CharSequence, Appendable {

    protected open val sliceMark: Any? get() = null

    final override val length: Int
        get() = sliceMark?.let { storage.getRangeForMark(it).length }
            ?: storage.length

    @Suppress("ReplaceGetOrSet")
    final override fun get(index: Int): Char = storage.get(index, sliceMark)

    /**
     * Returns a [Slice] that is a view of a [range] of this buffer. Edits to the returned
     * buffer will be reflected in the source, and edits to the source within the range will be
     * reflected in the view. The returned [Slice] must be [disposed][Slice.dispose] when no longer
     * needed, or it will leak resources required to track the slice's range in the buffer.
     *
     * To get a subsequence of the buffer that will not reflect changes but doesn't require disposal
     * use [subSequence].
     */
    fun slice(range: TextRange): Slice = Slice.create(storage, range, sliceMark)

    /**
     * Returns the substring of this buffer between [startIndex] (inclusive) and [endIndex]
     * (exclusive). The returned [CharSequence] will not reflect any subsequent changes to this
     * buffer.
     *
     * To get a view of this buffer that will reflect changes, use [slice].
     */
    final override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        contentsToString(TextRange(startIndex, endIndex))

    fun getChars(
        srcBegin: Int,
        srcEnd: Int,
        dest: CharArray,
        destBegin: Int
    ) = storage.getChars(srcBegin, srcEnd, dest, destBegin, sliceMark)

    final override fun toString(): String = "TextBuffer(\"${contentsToString()}\")"

    fun contentsToString(range: TextRange = TextRange.Unspecified): String =
        storage.contentsToString(range, sliceMark)

    final override fun append(chars: CharSequence): Appendable = apply {
        replace(TextRange(length), chars)
    }

    final override fun append(chars: CharSequence, start: Int, end: Int): Appendable =
        append(chars.subSequence(start, end))

    final override fun append(char: Char): Appendable = apply {
        replace(TextRange(length), char)
    }

    fun replace(range: TextRange = TextRange.Unspecified, replacement: Char) =
        storage.replace(range, replacement, sliceMark)

    fun <T> replace(
        range: TextRange = TextRange.Unspecified,
        replacement: T,
        replacementRange: TextRange,
        getCharsTrait: GetCharsTrait<T>
    ) = storage.replace(range, replacement, replacementRange, sliceMark, getCharsTrait)

    fun setAnnotation(annotation: String, tag: String, range: TextRange) {
        val marker = Annotation(annotation, tag)
        storage.markRange(range, marker, sliceMark)
    }

    fun getAnnotations(
        annotation: String,
        range: TextRange = TextRange.Unspecified,
    ): List<Pair<String, TextRange>> =
        storage.getMarksIntersecting(range, sliceMark) { mark, markedRange ->
            if ((mark as? Annotation)?.annotation == annotation) {
                Pair(mark.tag, markedRange)
            } else {
                null
            }
        }

    fun removeAnnotations(
        annotation: String,
        tag: String,
        range: TextRange = TextRange.Unspecified,
    ) {
        storage.getMarksIntersecting(range, sliceMark) { mark, _ ->
            (mark as? Annotation)?.takeIf { it.annotation == annotation && it.tag == tag }
        }.forEach(storage::unmark)
    }

    inline fun <R> withCharacterIterator(range: TextRange, block: (CharacterIterator) -> R): R =
        createCharacterIterator(range).use(block)

    @PublishedApi
    internal fun createCharacterIterator(range: TextRange): CharacterIterator =
        TextBufferCharacterIterator.create(storage, sliceMark, range)

    private data class Annotation(val annotation: String, val tag: String)

    class Slice private constructor(private val storage: TextBufferStorage) : TextBuffer(storage) {
        override val sliceMark get() = this

        // block takes a TextBuffer to hide the dispose method from it – only our finally block
        // can dispose.
        inline fun <R> use(block: (TextBuffer) -> R): R {
            try {
                return block(this)
            } finally {
                dispose()
            }
        }

        fun dispose() {
            storage.unmark(sliceMark)
        }

        internal companion object {
            @JvmStatic
            internal fun create(storage: TextBufferStorage, range: TextRange, sourceMark: Any?) =
                Slice(storage).also {
                    storage.markRange(range, newMark = it, sourceMark = sourceMark)
                }
        }
    }

    companion object : GetCharsTrait<TextBuffer> {
        override fun getChars(
            src: TextBuffer,
            srcBegin: Int,
            srcEnd: Int,
            dest: CharArray,
            destBegin: Int
        ) {
            src.getChars(srcBegin, srcEnd, dest, destBegin)
        }
    }
}

private val charSequenceGetCharsTrait =
    GetCharsTrait<CharSequence> { src, srcBegin, srcEnd, dest, destBegin ->
        val length = srcEnd - srcBegin
        for (i in 0 until length) {
            dest[destBegin + i] = src[srcBegin + i]
        }
    }

fun TextBuffer.replace(
    range: TextRange = TextRange.Unspecified,
    replacement: CharSequence,
    replacementRange: TextRange = TextRange(0, replacement.length)
): Unit = when (replacement) {
    is String -> replace(range, replacement, replacementRange)
    is StringBuilder -> replace(range, replacement, replacementRange)
    is StringBuffer -> replace(range, replacement, replacementRange)
    is TextBuffer -> replace(range, replacement, replacementRange)
    is TextBufferStorage -> replace(range, replacement as TextBufferStorage, replacementRange)
    is GetCharsTrait<*> -> {
        // Assume it's self-typed.
        @Suppress("UNCHECKED_CAST")
        val trait = replacement as GetCharsTrait<CharSequence>
        replace(range, replacement, replacementRange, trait)
    }

    // Slow path if we don't have access to a getChars method.
    else -> replace(range, replacement, replacementRange, charSequenceGetCharsTrait)
}

@PublishedApi
@JvmField
internal val charArrayGetCharsTrait =
    GetCharsTrait<CharArray> { src, srcBegin, srcEnd, dest, destBegin ->
        System.arraycopy(src, srcBegin, dest, destBegin, destBegin + (srcEnd - srcBegin))
    }

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = TextRange.Unspecified,
    replacement: CharArray,
    replacementRange: TextRange = TextRange(0, replacement.size)
) = replace(range, replacement, replacementRange, charArrayGetCharsTrait)

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = TextRange.Unspecified,
    replacement: StringBuilder,
    replacementRange: TextRange = TextRange(0, replacement.length)
) = replace(range, replacement, replacementRange, StringBuilder::getChars)

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = TextRange.Unspecified,
    replacement: StringBuffer,
    replacementRange: TextRange = TextRange(0, replacement.length)
) = replace(range, replacement, replacementRange, StringBuffer::getChars)

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = TextRange.Unspecified,
    replacement: String,
    replacementRange: TextRange = TextRange(0, replacement.length)
) {
    @Suppress("UNCHECKED_CAST")
    replace(
        range,
        replacement,
        replacementRange,
        java.lang.String::getChars as GetCharsTrait<String>
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = TextRange.Unspecified,
    replacement: TextBuffer,
    replacementRange: TextRange = TextRange(0, replacement.length)
) = replace(range, replacement, replacementRange, TextBuffer)

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = TextRange.Unspecified,
    replacement: TextBufferStorage,
    replacementRange: TextRange = TextRange(0, replacement.length)
) = replace(range, replacement, replacementRange, TextBufferStorage)

@Suppress("NOTHING_TO_INLINE")
@JvmName("replaceWithGetChars")
inline fun <T> TextBuffer.replace(
    range: TextRange = TextRange.Unspecified,
    replacement: T,
    replacementRange: TextRange = TextRange(0, replacement.length)
) where T : CharSequence, T : GetCharsTrait<T> =
    replace(range, replacement, replacementRange, replacement)