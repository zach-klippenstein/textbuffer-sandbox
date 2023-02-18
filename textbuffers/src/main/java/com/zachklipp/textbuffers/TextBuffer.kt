package com.zachklipp.textbuffers

/**
 * A mutable text buffer that is backed by a [TextBufferStorage].
 *
 * This is basically just a wrapper for [TextBufferStorage] that provides more convenient public
 * APIs. In a KMP library, this class would be expect/actual and implement all the relevant
 * platform-specific text-related types.
 */
class TextBuffer(private val storage: TextBufferStorage) : CharSequence, Appendable {

    override val length: Int get() = storage.length
    val range: TextRange get() = TextRange(0, length)

    override fun get(index: Int): Char = storage[index]

    override fun subSequence(startIndex: Int, endIndex: Int): TextBuffer =
        throw NotImplementedError()

    fun getChars(
        srcBegin: Int,
        srcEnd: Int,
        dest: CharArray,
        destBegin: Int
    ) = storage.getChars(srcBegin, srcEnd, dest, destBegin)

    override fun toString(): String = "TextBuffer(\"${contentsToString()}\")"

    fun contentsToString(range: TextRange = this.range): String =
        storage.contentsToString(range)

    override fun append(chars: CharSequence): Appendable = apply {
        replace(TextRange(length), chars)
    }

    override fun append(chars: CharSequence, start: Int, end: Int): Appendable =
        append(chars.subSequence(start, end))

    override fun append(char: Char): Appendable = apply {
        replace(TextRange(length), char)
    }

    fun replace(range: TextRange = this.range, replacement: Char) =
        storage.replace(range, replacement)

    context(GetCharsTrait<T>)
    fun <T> replace(
        range: TextRange = this.range,
        replacement: T,
        replacementRange: TextRange
    ) = storage.replace(range, replacement, replacementRange)

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
    range: TextRange = this.range,
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
    range: TextRange = this.range,
    replacement: CharArray,
    replacementRange: TextRange = TextRange(0, replacement.size)
) = replace(range, replacement, replacementRange, charArrayGetCharsTrait)

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = this.range,
    replacement: StringBuilder,
    replacementRange: TextRange = TextRange(0, replacement.length)
) = replace(range, replacement, replacementRange, StringBuilder::getChars)

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = this.range,
    replacement: StringBuffer,
    replacementRange: TextRange = TextRange(0, replacement.length)
) = replace(range, replacement, replacementRange, StringBuffer::getChars)

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = this.range,
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
    range: TextRange = this.range,
    replacement: TextBuffer,
    replacementRange: TextRange = replacement.range
) = replace(range, replacement, replacementRange, TextBuffer)

@Suppress("NOTHING_TO_INLINE")
inline fun TextBuffer.replace(
    range: TextRange = this.range,
    replacement: TextBufferStorage,
    replacementRange: TextRange = TextRange(0, replacement.length)
) = replace(range, replacement, replacementRange, TextBufferStorage)

@Suppress("NOTHING_TO_INLINE")
@JvmName("replaceWithGetChars")
inline fun <T> TextBuffer.replace(
    range: TextRange = this.range,
    replacement: T,
    replacementRange: TextRange = TextRange(0, replacement.length)
) where T : CharSequence, T : GetCharsTrait<T> =
    replace(range, replacement, replacementRange, replacement)

@Suppress("NOTHING_TO_INLINE")
@PublishedApi
internal inline fun <T> TextBuffer.replace(
    range: TextRange,
    replacement: T,
    replacementRange: TextRange,
    getCharsTrait: GetCharsTrait<T>
) {
    with(getCharsTrait) {
        replace(range, replacement, replacementRange)
    }
}