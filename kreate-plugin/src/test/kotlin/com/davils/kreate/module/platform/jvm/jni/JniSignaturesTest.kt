/*
 * Copyright 2026 Davils
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.davils.kreate.module.platform.jvm.jni

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Tests for [JniSignatures].
 *
 * The mangling rules are the contract between the JVM and the C++ side. A wrong name still
 * compiles on both sides and only fails at runtime, so these cases are the safety net that
 * makes generated headers trustworthy.
 */
@DisplayName("JniSignatures")
class JniSignaturesTest {

    @Nested
    @DisplayName("name mangling")
    inner class NameMangling {

        @Test
        @DisplayName("uses the short form for a uniquely named method")
        fun shortForm() {
            val method = NativeMethod("com/davils/example/JNI", "hello", "()Ljava/lang/String;", isStatic = false)

            JniSignatures.mangledName(method, useLongForm = false) shouldBe
                "Java_com_davils_example_JNI_hello"
        }

        @Test
        @DisplayName("appends the argument signature for overloaded methods")
        fun longForm() {
            val method = NativeMethod("com/example/Foo", "bar", "(ILjava/lang/String;)V", isStatic = false)

            JniSignatures.mangledName(method, useLongForm = true) shouldBe
                "Java_com_example_Foo_bar__ILjava_lang_String_2"
        }

        @Test
        @DisplayName("escapes underscores so that they cannot collide with package separators")
        fun escapesUnderscore() {
            // Without escaping, package `a_b.C` and package `a.b.C` would mangle identically.
            val method = NativeMethod("a_b/C", "do_it", "()V", isStatic = false)

            JniSignatures.mangledName(method, useLongForm = false) shouldBe "Java_a_1b_C_do_1it"
        }

        @Test
        @DisplayName("escapes non-ASCII characters as _0xxxx")
        fun escapesNonAscii() {
            val method = NativeMethod("com/example/Foo", "grüßen", "()V", isStatic = false)

            // ü is U+00FC and ß is U+00DF; the surrounding ASCII letters pass through.
            JniSignatures.mangledName(method, useLongForm = false) shouldBe
                "Java_com_example_Foo_gr_000fc_000dfen"
        }

        @Test
        @DisplayName("escapes array markers in the long form")
        fun escapesArrayMarker() {
            val method = NativeMethod("com/example/Foo", "bar", "([I)V", isStatic = false)

            JniSignatures.mangledName(method, useLongForm = true) shouldBe "Java_com_example_Foo_bar___3I"
        }
    }

    @Nested
    @DisplayName("declaration rendering")
    inner class DeclarationRendering {

        @Test
        @DisplayName("passes a jobject receiver for instance methods")
        fun instanceReceiver() {
            val declarations = JniSignatures.renderDeclarations(
                listOf(NativeMethod("com/example/Foo", "bar", "()V", isStatic = false))
            )

            declarations.single() shouldContain "(JNIEnv *env, jobject receiver)"
        }

        @Test
        @DisplayName("passes a jclass receiver for static methods")
        fun staticReceiver() {
            val declarations = JniSignatures.renderDeclarations(
                listOf(NativeMethod("com/example/Foo", "bar", "()V", isStatic = true))
            )

            declarations.single() shouldContain "(JNIEnv *env, jclass receiver)"
        }

        @Test
        @DisplayName("switches every overload of a name to the long form")
        fun overloadsUseLongForm() {
            val declarations = JniSignatures.renderDeclarations(
                listOf(
                    NativeMethod("com/example/Foo", "bar", "(I)V", isStatic = false),
                    NativeMethod("com/example/Foo", "bar", "(J)V", isStatic = false)
                )
            )

            declarations[0] shouldContain "Java_com_example_Foo_bar__I"
            declarations[1] shouldContain "Java_com_example_Foo_bar__J"
        }

        @Test
        @DisplayName("keeps the short form when names do not collide")
        fun distinctNamesUseShortForm() {
            val declarations = JniSignatures.renderDeclarations(
                listOf(
                    NativeMethod("com/example/Foo", "bar", "(I)V", isStatic = false),
                    NativeMethod("com/example/Foo", "baz", "(J)V", isStatic = false)
                )
            )

            declarations[0] shouldContain "JNICALL Java_com_example_Foo_bar\n"
            declarations[1] shouldContain "JNICALL Java_com_example_Foo_baz\n"
        }

        @Test
        @DisplayName("numbers the declared arguments after the receiver")
        fun argumentNaming() {
            val declarations = JniSignatures.renderDeclarations(
                listOf(NativeMethod("com/example/Foo", "bar", "(IJ)V", isStatic = false))
            )

            declarations.single() shouldContain "(JNIEnv *env, jobject receiver, jint arg1, jlong arg2)"
        }

        @Test
        @DisplayName("documents the originating class, method and descriptor")
        fun rendersProvenanceComment() {
            val declarations = JniSignatures.renderDeclarations(
                listOf(NativeMethod("com/example/Foo", "bar", "()I", isStatic = false))
            )

            declarations.single() shouldContain "Class:     com.example.Foo"
            declarations.single() shouldContain "Signature: ()I"
        }
    }

    @Nested
    @DisplayName("descriptor to JNI type mapping")
    inner class TypeMapping {

        @ParameterizedTest(name = "{0} maps to {1}")
        @CsvSource(
            "Z, jboolean",
            "B, jbyte",
            "C, jchar",
            "S, jshort",
            "I, jint",
            "J, jlong",
            "F, jfloat",
            "D, jdouble",
            "Ljava/lang/String;, jstring",
            "Ljava/lang/Class;, jclass",
            "Ljava/lang/Throwable;, jthrowable",
            "Ljava/util/List;, jobject",
            "[Z, jbooleanArray",
            "[I, jintArray",
            "[Ljava/lang/String;, jobjectArray",
            "[[I, jobjectArray"
        )
        fun mapsArgumentTypes(descriptor: String, expected: String) {
            val declarations = JniSignatures.renderDeclarations(
                listOf(NativeMethod("com/example/Foo", "bar", "($descriptor)V", isStatic = false))
            )

            declarations.single() shouldContain "$expected arg1"
        }

        @ParameterizedTest(name = "return {0} maps to {1}")
        @CsvSource(
            "V, void",
            "I, jint",
            "Ljava/lang/String;, jstring",
            "[B, jbyteArray"
        )
        fun mapsReturnTypes(descriptor: String, expected: String) {
            val declarations = JniSignatures.renderDeclarations(
                listOf(NativeMethod("com/example/Foo", "bar", "()$descriptor", isStatic = false))
            )

            declarations.single() shouldContain "JNIEXPORT $expected JNICALL"
        }
    }
}
