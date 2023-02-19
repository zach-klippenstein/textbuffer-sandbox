package com.zachklipp.textbuffers.storage

import com.google.common.truth.FailureMetadata
import com.google.common.truth.IntegerSubject
import com.google.common.truth.StringSubject
import com.google.common.truth.Subject
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.zachklipp.textbuffers.GetCharsTrait
import com.zachklipp.textbuffers.TextRange
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("JUnitMalformedDeclaration", "unused")
@RunWith(TestParameterInjector::class)
class StringBuilderTextBufferStorageTest {

    enum class BufferImpl(
        val factory: () -> TextBufferStorage,
        val snapshotAware: Boolean = true,
        val supportsMarks: Boolean = true
    ) {
        StringBuilderStorage(
            ::StringBuilderTextBufferStorage,
            snapshotAware = false,
            supportsMarks = false
        ),
        GapBufferStorage(
            ::GapBufferStorage,
            snapshotAware = false,
            supportsMarks = false
        ),
    }

    enum class ReplacementCase(
        val initial: String,
        val replacement: String,
        val expected: String,
        val range: TextRange = TextRange.Unspecified,
        val replacementRange: TextRange = TextRange.Unspecified,
        val sourceMark: Any? = null,
    ) {
        EmptyWithEmptyZero(initial = "", replacement = "", expected = "", range = TextRange.Zero),
        EmptyWithEmptyUnspecified(initial = "", replacement = "", expected = ""),
        EmptyZero(initial = "", replacement = "hello", expected = "hello", range = TextRange.Zero),
        EmptyUnspecified(initial = "", replacement = "hello", expected = "hello"),
        AllWithEmpty(initial = "hello", replacement = "", expected = ""),
        AllWithShorter(initial = "foo", replacement = "b", expected = "b"),
        AllWithSameLength(initial = "foo", replacement = "bar", expected = "bar"),
        AllWithLonger(initial = "foo", replacement = "barbaz", expected = "barbaz"),
        ReplacePrefix(
            initial = "foobar",
            range = TextRange(0, 3),
            replacement = "baz",
            expected = "bazbar"
        ),
        ReplaceMiddle(
            initial = "foobar",
            range = TextRange(1, 5),
            replacement = "baz",
            expected = "fbazr"
        ),
        ReplaceSuffix(
            initial = "foobar",
            range = TextRange(3, 6),
            replacement = "baz",
            expected = "foobaz"
        ),
        InsertPrefix(
            initial = "foobar",
            range = TextRange(0),
            replacement = "baz",
            expected = "bazfoobar"
        ),
        InsertMiddle(
            initial = "foobar",
            range = TextRange(3),
            replacement = "baz",
            expected = "foobazbar"
        ),
        InsertSuffix(
            initial = "foobar",
            range = TextRange(6),
            replacement = "baz",
            expected = "foobarbaz"
        ),
        // TODO replacement range
    }

    @TestParameter
    private lateinit var bufferImpl: BufferImpl
    private val buffer by lazy(LazyThreadSafetyMode.NONE) { bufferImpl.factory() }

    @Test
    fun `empty buffer`() {
        assertThat(buffer).length.isEqualTo(0)
        assertThat(buffer).contents.isEmpty()
        assertThat(buffer.toString()).isEqualTo("()")
    }

    @Test
    fun `insert char to empty buffer at zero`() {
        buffer.replace(TextRange.Zero, 'a')

        assertThat(buffer).length.isEqualTo(1)
        assertThat(buffer).contents.isEqualTo("a")
    }

    @Test
    fun `insert char to empty buffer at unspecified`() {
        buffer.replace(TextRange.Unspecified, 'a')

        assertThat(buffer).length.isEqualTo(1)
        assertThat(buffer).contents.isEqualTo("a")
    }

    @Test
    fun replace(@TestParameter case: ReplacementCase) {
        if (case.sourceMark != null) assume().that(bufferImpl.supportsMarks).isTrue()
        if (case.initial.isNotEmpty()) {
            buffer.replace(case.initial)
        }
        assertThat(buffer).contents.isEqualTo(case.initial)

        buffer.replace(
            range = case.range,
            replacement = case.replacement,
            replacementRange = case.replacementRange
        )

        assertThat(buffer).length.isEqualTo(case.expected.length)
        assertThat(buffer).contents.isEqualTo(case.expected)
    }

    @Test
    fun `replace inside snapshot`(@TestParameter case: ReplacementCase) {
        assume().that(bufferImpl.snapshotAware).isTrue()
        if (case.sourceMark != null) assume().that(bufferImpl.supportsMarks).isTrue()
        if (case.initial.isNotEmpty()) {
            buffer.replace(case.initial)
        }
        assertThat(buffer).contents.isEqualTo(case.initial)

        // Snapshot.withMutableSnapshot
        buffer.replace(
            range = case.range,
            replacement = case.replacement,
            replacementRange = case.replacementRange
        )
        assertThat(buffer).length.isEqualTo(case.expected.length)
        assertThat(buffer).contents.isEqualTo(case.expected)
        //

        assertThat(buffer).length.isEqualTo(case.expected.length)
        assertThat(buffer).contents.isEqualTo(case.expected)
    }

    @Test
    fun `change inside snapshot discarded`(@TestParameter case: ReplacementCase) {
        assume().that(bufferImpl.snapshotAware).isTrue()
        if (case.sourceMark != null) assume().that(bufferImpl.supportsMarks).isTrue()
        if (case.initial.isNotEmpty()) {
            buffer.replace(case.initial)
        }
        assertThat(buffer).contents.isEqualTo(case.initial)

        // Snapshot.withMutableSnapshot
        buffer.replace(
            range = case.range,
            replacement = case.replacement,
            replacementRange = case.replacementRange
        )
        assertThat(buffer).length.isEqualTo(case.expected.length)
        assertThat(buffer).contents.isEqualTo(case.expected)
        // snapshot.dispose() without applying

        assertThat(buffer).length.isEqualTo(case.initial.length)
        assertThat(buffer).contents.isEqualTo(case.initial)
    }

    @Test
    fun `mark tracks changes before`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes inside`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes after`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes intersecting start`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes intersecting end`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    @Test
    fun `mark tracks changes intersecting all`() {
        assume().that(bufferImpl.supportsMarks).isTrue()
        TODO()
    }

    private fun TextBufferStorage.replace(
        replacement: String,
        range: TextRange = TextRange.Unspecified,
        replacementRange: TextRange = TextRange.Unspecified
    ) {
        with(GetCharsTrait<String> { src, srcBegin, srcEnd, dest, destBegin ->
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "KotlinConstantConditions")
            (src as java.lang.String).getChars(srcBegin, srcEnd, dest, destBegin)
        }) {
            replace(range, replacement, replacementRange)
        }
    }

    private fun assertThat(buffer: TextBufferStorage): TextBufferStorageSubject =
        assertAbout(::TextBufferStorageSubject).that(buffer)

    private class TextBufferStorageSubject(
        metadata: FailureMetadata,
        private val actual: TextBufferStorage
    ) : Subject(metadata, actual) {
        val length: IntegerSubject get() = check("length").that(actual.length)
        val contents: StringSubject get() = check("contents").that(actual.contentsToString())
    }
}
