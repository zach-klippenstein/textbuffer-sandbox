package com.zachklipp.textbuffers

import java.nio.CharBuffer

fun interface GetCharsTrait<in T> {
    fun getChars(src: T, srcBegin: Int, srcEnd: Int, dest: CharArray, destBegin: Int)
}

abstract class TextBuffer {
    abstract val length: Int

    abstract fun replace(
        begin: Int = 0,
        end: Int = length,
        replacement: Char
    )

    abstract fun <T> replace(
        begin: Int = 0,
        end: Int = length,
        replacement: T,
        replacementStart: Int,
        replacementEnd: Int,
        getCharsTrait: GetCharsTrait<T>
    )

    abstract operator fun get(index: Int): Char
    abstract fun getChars(srcBegin: Int, srcEnd: Int, dest: CharArray, destBegin: Int)

    final override fun toString(): String = toString(0, length)

    fun toString(start: Int = 0, end: Int = length): String {
        val chars = CharArray(end - start)
        getChars(start, end, chars, 0)
        return String(chars)
    }
}

private val charArrayGetCharsTrait =
    GetCharsTrait<CharArray> { src, srcBegin, srcEnd, dest, destBegin ->
        System.arraycopy(src, srcBegin, dest, destBegin, destBegin + (srcEnd - srcBegin))
    }

//fun TextBuffer.replace(
//    begin: Int = 0,
//    end: Int = length,
//    replacement: CharArray,
//    replacementStart: Int = 0,
//    replacementEnd: Int = replacement.size
//) {
//    replace(begin, end, replacement, replacementStart, replacementEnd, charArrayGetCharsTrait)
//}

fun TextBuffer.replace(
    begin: Int = 0,
    end: Int = length,
    replacement: StringBuilder,
    replacementStart: Int = 0,
    replacementEnd: Int = replacement.length
) {
    replace(begin, end, replacement, replacementStart, replacementEnd, StringBuilder::getChars)
}

fun TextBuffer.replace(
    begin: Int = 0,
    end: Int = length,
    replacement: StringBuffer,
    replacementStart: Int = 0,
    replacementEnd: Int = replacement.length
) {
    replace(begin, end, replacement, replacementStart, replacementEnd, StringBuffer::getChars)
}

private val charBufferGetCharsTrait =
    GetCharsTrait<CharBuffer> { src, srcBegin, srcEnd, dest, destBegin ->
        src.get(srcBegin, dest, destBegin, srcEnd - srcBegin)
    }

fun TextBuffer.replace(
    begin: Int = 0,
    end: Int = length,
    replacement: CharBuffer,
    replacementStart: Int = 0,
    replacementEnd: Int = replacement.length
) {
    replace(begin, end, replacement, replacementStart, replacementEnd, charBufferGetCharsTrait)
}

fun TextBuffer.replace(
    begin: Int = 0,
    end: Int = length,
    replacement: String,
    replacementStart: Int = 0,
    replacementEnd: Int = replacement.length
) {
    @Suppress("UNCHECKED_CAST")
    replace(
        begin,
        end,
        replacement,
        replacementStart,
        replacementEnd,
        java.lang.String::getChars as GetCharsTrait<String>
    )
}

//fun TextBuffer.replace(
//    begin: Int = 0,
//    end: Int = length,
//    replacement: TextBuffer,
//    replacementStart: Int = 0,
//    replacementEnd: Int = replacement.length
//) {
//    replace(begin, end, replacement, replacementStart, replacementEnd, TextBuffer::getChars)
//}

//fun <T> TextBuffer.replace(
//    begin: Int = 0,
//    end: Int = length,
//    replacement: T,
//    replacementStart: Int = 0,
//    replacementEnd: Int = replacement.length
//) where T : CharSequence, T : GetCharsTrait<T> {
//    replace(begin, end, replacement, replacementStart, replacementEnd, replacement)
//}

fun TextBuffer.replace(
    begin: Int = 0,
    end: Int = length,
    replacement: CharSequence,
    replacementStart: Int = 0,
    replacementEnd: Int = replacement.length
) {
    when (replacement) {
        is String -> replace(begin, end, replacement, replacementStart, replacementEnd)
        is StringBuilder -> replace(begin, end, replacement, replacementStart, replacementEnd)
        is StringBuffer -> replace(begin, end, replacement, replacementStart, replacementEnd)
        is CharBuffer -> replace(begin, end, replacement, replacementStart, replacementEnd)
        is GetCharsTrait<*> -> {
            // Assume it's self-typed.
            @Suppress("UNCHECKED_CAST")
            replace(
                begin,
                end,
                replacement,
                replacementStart,
                replacementEnd,
                replacement as GetCharsTrait<CharSequence>
            )
        }

        else -> replace(begin, end, replacement.substring(replacementStart, replacementEnd))
    }
}

fun TextBuffer.append(chars: CharSequence) {
    replace(length, length, chars)
}

fun TextBuffer.append(chars: CharSequence, start: Int, end: Int) {
    append(chars.subSequence(start, end))
}

fun TextBuffer.append(char: Char) {
    replace(length, length, char)
}

fun TextBuffer.asAppendable(): Appendable = object : Appendable {
    override fun append(chars: CharSequence): Appendable =
        apply { this@asAppendable.append(chars) }

    override fun append(chars: CharSequence, start: Int, end: Int): Appendable =
        apply { this@asAppendable.append(chars, start, end) }

    override fun append(char: Char): Appendable =
        apply { this@asAppendable.append(char) }
}

fun TextBuffer.asCharSequence(start: Int = 0, end: Int = -1): CharSequence {
    class TextBufferCharSequence : CharSequence, GetCharsTrait<TextBufferCharSequence> {
        val buffer = this@asCharSequence

        override val length: Int
            get() = if (end == -1) buffer.length - start else end - start

        override fun get(index: Int): Char = buffer[start + index]

        override fun subSequence(startIndex: Int, endIndex: Int) =
            buffer.asCharSequence(start + startIndex, start + endIndex)

        override fun getChars(
            src: TextBufferCharSequence,
            srcBegin: Int,
            srcEnd: Int,
            dest: CharArray,
            destBegin: Int
        ) = src.buffer.getChars(srcBegin, srcEnd, dest, destBegin)

        override fun toString(): String = buffer.toString(start, start + length)
    }
    return TextBufferCharSequence()
}

fun StringBuilder.asTextBuffer(): TextBuffer = object : TextBuffer() {
    override val length: Int
        get() = this@asTextBuffer.length

    override fun replace(begin: Int, end: Int, replacement: Char) {
        this@asTextBuffer.replace(begin, end, replacement.toString())
    }

    override fun <T> replace(
        begin: Int,
        end: Int,
        replacement: T,
        replacementStart: Int,
        replacementEnd: Int,
        getCharsTrait: GetCharsTrait<T>
    ) {
        this@asTextBuffer.replace(begin, end, toString(replacementStart, replacementEnd))
        // TODO delete then insert to avoid String's defensive copy?
    }

    override fun get(index: Int): Char = this@asTextBuffer[index]

    override fun getChars(srcBegin: Int, srcEnd: Int, dest: CharArray, destBegin: Int) {
        this@asTextBuffer.getChars(srcBegin, srcEnd, dest, destBegin)
    }
}