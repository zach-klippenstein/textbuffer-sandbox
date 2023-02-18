package com.zachklipp.textbuffers.demo

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.zachklipp.textbuffers.TextBuffer
import com.zachklipp.textbuffers.asTextBuffer
import com.zachklipp.textbuffers.replace
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays

// TODO this is failing because unable to grant WRITE_EXTERNAL_STORAGE permission.
@RunWith(TestParameterInjector::class)
class TextBuffersBenchmark {

    @Suppress("unused")
    enum class BufferImplementation(val factory: () -> TextBuffer) {
        StringBuilder({ StringBuilder().asTextBuffer() }),
    }

    @get:Rule
    val rule = BenchmarkRule()

    @TestParameter("10", "32", "64", "128", "256", "512", "1024", "4096", "1000000")
    private var bufferSize: Int = 0

    @TestParameter
    private lateinit var implementation: BufferImplementation

    @Test
    fun appendFromEmpty() {
        lateinit var buffer: TextBuffer
        lateinit var check: StringBuilder

        rule.measureRepeated {
            buffer = runWithTimingDisabled {
                implementation.factory()
            }
            check = runWithTimingDisabled { StringBuilder() }

            for (i in 0 until bufferSize) {
                // Don't use length because that costs a virtual method call.
                buffer.replace(i, i, 'a')
                runWithTimingDisabled { check.append('a') }
            }
        }

        assertEquals(check.toString(), buffer.toString())
    }

    @Test
    fun appendOneAndRemove() {
        val buffer = implementation.factory().apply { fill('a', bufferSize) }
        val check = StringBuilder().apply { fill('a', bufferSize) }

        rule.measureRepeated {
            buffer.replace(bufferSize, bufferSize, 'a')
            buffer.replace(bufferSize, bufferSize + 1, "")

            runWithTimingDisabled {
                check.replace(bufferSize, bufferSize, "a")
                check.removeRange(bufferSize, bufferSize + 1)
            }
        }

        assertEquals(check.toString(), buffer.toString())
    }

    private fun StringBuilder.fill(char: Char, count: Int) {
        for (i in 0 until count) {
            append(char)
        }
    }

    private fun TextBuffer.fill(char: Char, count: Int) {
        replace(
            replacement = char,
            replacementStart = 0,
            replacementEnd = count
        ) { src, srcBegin, srcEnd, dest, destBegin ->
            Arrays.fill(dest, destBegin, srcEnd - srcBegin, src)
        }
    }
}