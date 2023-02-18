package com.zachklipp.textbuffers

fun interface GetCharsTrait<in T> {
    fun getChars(src: T, srcBegin: Int, srcEnd: Int, dest: CharArray, destBegin: Int)
}