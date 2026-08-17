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

package com.davils.kreate.module.platform.jvm.jni.tasks

import com.davils.kreate.jobs.Task
import com.davils.kreate.module.platform.jvm.jni.JniSignatures
import com.davils.kreate.module.platform.jvm.jni.NativeMethod
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Generates a JNI header from the `native` methods of the project's compiled classes.
 *
 * Kotlin has no counterpart to `javac -h`, so the C function signatures that back an
 * `external fun` normally have to be transcribed by hand. Because a mismatched signature
 * still compiles on both sides, the mistake only surfaces at runtime as an
 * `UnsatisfiedLinkError`. This task removes the transcription step: it reads the compiled
 * class files, finds every method with the `ACC_NATIVE` flag, and emits the exact
 * declarations the JNI expects.
 *
 * The output directory is added to the CMake include path automatically, so a native
 * source file only has to include the generated header to be checked by the C++ compiler.
 *
 * @since 2.0.0
 */
@CacheableTask
public abstract class GenerateJniHeaders : Task(
    "Generates a JNI header from the native methods of the compiled classes.",
    "kreate jni"
) {
    /**
     * The compiled class directories that are scanned for `native` methods.
     *
     * @since 2.0.0
     */
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val classDirectories: ConfigurableFileCollection

    /**
     * The file name of the generated header.
     *
     * @since 2.0.0
     */
    @get:Input
    public abstract val headerFileName: Property<String>

    /**
     * The directory the header is written to.
     *
     * @since 2.0.0
     */
    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    /**
     * Scans the class files and writes the generated header.
     *
     * @return Unit
     * @since 2.0.0
     */
    @TaskAction
    public fun execute() {
        val methods = classDirectories.asFileTree
            .matching { include("**/*.class") }
            .files
            .sortedBy { it.absolutePath }
            .flatMap { classFile -> readNativeMethods(classFile.readBytes()) }

        val targetDirectory = outputDirectory.get().asFile
        targetDirectory.mkdirs()
        val header = targetDirectory.resolve(headerFileName.get())

        if (methods.isEmpty()) {
            logger.info("No native methods found; writing an empty JNI header to $header.")
        } else {
            logger.lifecycle("Generated JNI declarations for ${methods.size} native method(s).")
        }

        header.writeText(renderHeader(methods))
    }

    /**
     * Extracts the `native` methods declared in a single class file.
     *
     * @param bytecode The raw contents of the class file.
     * @return The native methods declared by that class, possibly empty.
     * @since 2.0.0
     */
    private fun readNativeMethods(bytecode: ByteArray): List<NativeMethod> {
        val methods = mutableListOf<NativeMethod>()

        ClassReader(bytecode).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                private var internalName = ""

                override fun visit(
                    version: Int,
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    internalName = name
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor? {
                    if (access and Opcodes.ACC_NATIVE != 0) {
                        methods += NativeMethod(
                            ownerInternalName = internalName,
                            name = name,
                            descriptor = descriptor,
                            isStatic = access and Opcodes.ACC_STATIC != 0
                        )
                    }
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
        )

        return methods
    }

    /**
     * Renders the complete header file for the given methods.
     *
     * @param methods Every native method found across all scanned classes.
     * @return The header file contents.
     * @since 2.0.0
     */
    private fun renderHeader(methods: List<NativeMethod>): String {
        val guard = headerFileName.get()
            .uppercase()
            .map { if (it in 'A'..'Z' || it in '0'..'9') it else '_' }
            .joinToString("")

        return buildString {
            appendLine("/* This file is generated by Kreate. Do not edit — it is overwritten on every build. */")
            appendLine("#ifndef $guard")
            appendLine("#define $guard")
            appendLine()
            appendLine("#include <jni.h>")
            appendLine()
            appendLine("#ifdef __cplusplus")
            appendLine("extern \"C\" {")
            appendLine("#endif")
            appendLine()

            methods.groupBy { it.ownerInternalName }
                .toSortedMap()
                .forEach { (_, classMethods) ->
                    JniSignatures.renderDeclarations(classMethods).forEach {
                        appendLine(it)
                        appendLine()
                    }
                }

            appendLine("#ifdef __cplusplus")
            appendLine("}")
            appendLine("#endif")
            appendLine()
            append("#endif /* $guard */")
        }
    }
}
