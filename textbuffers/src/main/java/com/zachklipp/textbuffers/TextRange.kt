package com.zachklipp.textbuffers

@JvmInline
value class TextRange private constructor(private val packed: Long) {
    constructor(index: Int) : this(index, index)
    constructor(startInclusive: Int, endExclusive: Int) : this(
        packInts(startInclusive, endExclusive)
    )

    val startInclusive: Int get() = unpackIntA(packed)
    val endExclusive: Int get() = unpackIntB(packed)

    val length: Int get() = endExclusive - startInclusive

    override fun toString(): String =
        "TextRange(startInclusive=$startInclusive, endExclusive=$endExclusive)"

    companion object {
        val Zero = TextRange(0)
        val Unspecified = TextRange(-1)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun packInts(a: Int, b: Int): Long {
    require(a <= b) { "Expected $a ≤ $b" }
    return a.toLong().shl(32) or (b.toLong() and 0xFFFFFFFF)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun unpackIntA(value: Long): Int = value.shr(32).toInt()

@Suppress("NOTHING_TO_INLINE")
private inline fun unpackIntB(value: Long): Int = value.and(0xFFFFFFFF).toInt()