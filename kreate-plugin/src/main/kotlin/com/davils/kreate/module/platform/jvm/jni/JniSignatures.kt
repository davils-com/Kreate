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

/**
 * A `native` method discovered in the project's compiled classes.
 *
 * @since 2.0.0
 */
internal data class NativeMethod(
    /**
     * The internal name of the declaring class, using slashes (`com/example/Foo`).
     *
     * @since 2.0.0
     */
    val ownerInternalName: String,
    /**
     * The method name as declared in the JVM class file.
     *
     * @since 2.0.0
     */
    val name: String,
    /**
     * The JVM method descriptor, for example `(Ljava/lang/String;)I`.
     *
     * @since 2.0.0
     */
    val descriptor: String,
    /**
     * Whether the method is static, which decides whether the second JNI parameter is a
     * `jclass` or a `jobject`.
     *
     * @since 2.0.0
     */
    val isStatic: Boolean
)

/**
 * Produces JNI C function signatures for `native` methods.
 *
 * The mangling rules implemented here are the ones specified by the JNI, the same ones
 * `javac -h` applies. They are reproduced rather than delegated to `javac`, because
 * `javac -h` only works on Java sources: Kotlin's `external` declarations never pass
 * through it, which is precisely why the signatures had to be written by hand before.
 *
 * @since 2.0.0
 */
internal object JniSignatures {
    /**
     * The JNI type for every descriptor the JNI names explicitly.
     *
     * Anything not listed is a plain reference and maps to `jobject`.
     *
     * @since 2.0.0
     */
    private val SCALAR_TYPES = mapOf(
        "Z" to "jboolean",
        "B" to "jbyte",
        "C" to "jchar",
        "S" to "jshort",
        "I" to "jint",
        "J" to "jlong",
        "F" to "jfloat",
        "D" to "jdouble",
        "V" to "void",
        "Ljava/lang/String;" to "jstring",
        "Ljava/lang/Class;" to "jclass",
        "Ljava/lang/Throwable;" to "jthrowable"
    )

    /**
     * Renders the declarations for all native methods of a single class.
     *
     * A short name (`Java_com_example_Foo_bar`) is used when the method name is unique
     * within its class. Overloaded native methods get the long form with the argument
     * signature appended, exactly as the JNI requires for them to be resolvable.
     *
     * @param methods The native methods of one class.
     * @return The rendered C declarations, one per method.
     * @since 2.0.0
     */
    fun renderDeclarations(methods: List<NativeMethod>): List<String> {
        val overloaded = methods.groupBy { it.name }
            .filterValues { it.size > 1 }
            .keys

        return methods.map { method ->
            val functionName = mangledName(method, useLongForm = method.name in overloaded)
            val parameters = parameterList(method)
            val returnType = jniType(returnDescriptor(method.descriptor))

            buildString {
                appendLine("/*")
                appendLine(" * Class:     ${method.ownerInternalName.replace('/', '.')}")
                appendLine(" * Method:    ${method.name}")
                appendLine(" * Signature: ${method.descriptor}")
                appendLine(" */")
                appendLine("JNIEXPORT $returnType JNICALL $functionName")
                append("  ($parameters);")
            }
        }
    }

    /**
     * Builds the mangled JNI function name for a method.
     *
     * @param method The native method.
     * @param useLongForm Whether the argument signature is appended after a double
     *   underscore, which is required to disambiguate overloads.
     * @return The mangled C function name.
     * @since 2.0.0
     */
    fun mangledName(method: NativeMethod, useLongForm: Boolean): String = buildString {
        append("Java_")
        append(mangle(method.ownerInternalName))
        append('_')
        append(mangle(method.name))
        if (useLongForm) {
            append("__")
            append(mangle(argumentDescriptors(method.descriptor)))
        }
    }

    /**
     * Applies the JNI name mangling rules to a single identifier fragment.
     *
     * @param value The raw fragment, such as an internal class name or a method name.
     * @return The mangled fragment.
     * @since 2.0.0
     */
    private fun mangle(value: String): String = buildString {
        for (character in value) {
            when {
                character.isAsciiAlphanumeric() -> append(character)
                character == '/' -> append('_')
                character == '_' -> append("_1")
                character == ';' -> append("_2")
                character == '[' -> append("_3")
                else -> append("_0%04x".format(character.code))
            }
        }
    }

    /**
     * Builds the JNI parameter list of a native method.
     *
     * Every JNI function receives the environment pointer first, then either the receiver
     * or the declaring class, followed by the declared arguments.
     *
     * @param method The native method.
     * @return The rendered C parameter list.
     * @since 2.0.0
     */
    private fun parameterList(method: NativeMethod): String {
        val receiver = if (method.isStatic) "jclass" else "jobject"
        val arguments = parseArgumentTypes(argumentDescriptors(method.descriptor))
            .mapIndexed { index, descriptor -> "${jniType(descriptor)} arg${index + 1}" }

        return (listOf("JNIEnv *env", "$receiver receiver") + arguments).joinToString(", ")
    }

    /**
     * Extracts the argument portion of a method descriptor.
     *
     * @param descriptor The full method descriptor.
     * @return The descriptor text between the parentheses.
     * @since 2.0.0
     */
    private fun argumentDescriptors(descriptor: String): String =
        descriptor.substringAfter('(').substringBeforeLast(')')

    /**
     * Extracts the return type portion of a method descriptor.
     *
     * @param descriptor The full method descriptor.
     * @return The descriptor following the closing parenthesis.
     * @since 2.0.0
     */
    private fun returnDescriptor(descriptor: String): String = descriptor.substringAfterLast(')')

    /**
     * Splits a descriptor argument list into individual type descriptors.
     *
     * @param arguments The descriptor text between the parentheses.
     * @return The individual argument type descriptors, in declaration order.
     * @since 2.0.0
     */
    private fun parseArgumentTypes(arguments: String): List<String> {
        val types = mutableListOf<String>()
        var index = 0

        while (index < arguments.length) {
            val start = index
            while (arguments[index] == '[') index++

            if (arguments[index] == 'L') {
                index = arguments.indexOf(';', index) + 1
            } else {
                index++
            }

            types += arguments.substring(start, index)
        }

        return types
    }

    /**
     * Maps a JVM type descriptor to the corresponding JNI C type.
     *
     * @param descriptor A single type descriptor, such as `I`, `[J` or `Ljava/lang/String;`.
     * @return The JNI C type name.
     * @since 2.0.0
     */
    private fun jniType(descriptor: String): String = when {
        // A multi-dimensional array and an array of references are both jobjectArray;
        // only an array of primitives gets its own typed array.
        descriptor.startsWith("[[") || descriptor.startsWith("[L") -> "jobjectArray"
        descriptor.startsWith("[") -> "${jniType(descriptor.substring(1))}Array"
        else -> SCALAR_TYPES[descriptor] ?: "jobject"
    }

    /**
     * Reports whether a character is an unaccented ASCII letter or digit.
     *
     * [Char.isLetterOrDigit] is Unicode aware and would leave characters such as `ä` in
     * place, which the JNI requires to be escaped instead.
     *
     * @return `true` when the character is `A`-`Z`, `a`-`z` or `0`-`9`.
     * @since 2.0.0
     */
    private fun Char.isAsciiAlphanumeric(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
