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

package com.davils.kreate.module.project.api

import org.objectweb.asm.Opcodes

/**
 * The kind of member a signature describes.
 *
 * @since 2.1.0
 */
internal enum class AbiMemberKind(
    /**
     * The keyword used for this kind in a rendered dump.
     * @since 2.1.0
     */
    val keyword: String
) {

    /**
     * A field.
     * @since 2.1.0
     */
    FIELD("field"),

    /**
     * A method or constructor.
     * @since 2.1.0
     */
    METHOD("fun")
}

/**
 * A single field or method that is part of a class's binary interface.
 *
 * @since 2.1.0
 */
internal data class AbiMember(
    /**
     * Whether this member is a field or a method.
     * @since 2.1.0
     */
    val kind: AbiMemberKind,
    /**
     * The raw ASM access flags of the member.
     * @since 2.1.0
     */
    val access: Int,
    /**
     * The member name, or `<init>` / `<clinit>` for constructors and initialisers.
     * @since 2.1.0
     */
    val name: String,
    /**
     * The JVM descriptor of the member.
     * @since 2.1.0
     */
    val descriptor: String
)

/**
 * A class that is part of the project's binary interface, together with the members that
 * belong to it.
 *
 * @since 2.1.0
 */
internal data class AbiClass(
    /**
     * The raw ASM access flags of the class.
     * @since 2.1.0
     */
    val access: Int,
    /**
     * The internal name of the class, for example `com/davils/kreate/Kreate`.
     * @since 2.1.0
     */
    val internalName: String,
    /**
     * The internal name of the superclass, or `null` for interfaces and for `java/lang/Object`.
     * @since 2.1.0
     */
    val superName: String?,
    /**
     * The internal names of the directly implemented interfaces.
     * @since 2.1.0
     */
    val interfaces: List<String>,
    /**
     * The members that survived the visibility filter.
     * @since 2.1.0
     */
    val members: List<AbiMember>
)

/**
 * Renders extracted signatures in the format used by the Kotlin binary compatibility
 * validator's `.api` files.
 *
 * The format is reproduced rather than invented so that a project migrating from the
 * `binary-compatibility-validator` plugin keeps its checked-in dump, its review habits and
 * its CI steps. Every detail below was taken from an existing dump: tab-indented members,
 * fields before methods, each group sorted by name and then descriptor, a blank line
 * between classes, and `java/lang/Object` omitted from the supertype list.
 *
 * @since 2.1.0
 */
internal object AbiRenderer {
    private const val OBJECT_INTERNAL_NAME = "java/lang/Object"

    /**
     * Renders a complete dump.
     *
     * @param classes The classes to render, in any order.
     * @return The dump text, ending in a blank line, or the empty string when there is
     *   nothing to render.
     * @since 2.1.0
     */
    fun render(classes: List<AbiClass>): String {
        if (classes.isEmpty()) return ""

        return classes
            .sortedBy { it.internalName }
            .joinToString(separator = "") { renderClass(it) }
    }

    /**
     * Renders a single class block, including its trailing newline.
     *
     * @param abiClass The class to render.
     * @return The rendered block.
     * @since 2.1.0
     */
    private fun renderClass(abiClass: AbiClass): String = buildString {
        append(modifiers(abiClass.access))
        append(' ')
        append(if (abiClass.access and Opcodes.ACC_INTERFACE != 0) "interface" else "class")
        append(' ')
        append(abiClass.internalName)

        val supertypes = buildList {
            abiClass.superName?.takeIf { it != OBJECT_INTERNAL_NAME }?.let(::add)
            addAll(abiClass.interfaces)
        }
        if (supertypes.isNotEmpty()) {
            append(" : ")
            append(supertypes.joinToString(", "))
        }

        appendLine(" {")
        sortMembers(abiClass.members).forEach { member ->
            append('\t')
            appendLine(renderMember(member))
        }
        appendLine("}")
        // Every block is followed by a blank line, the last one included: that is what the
        // validator's own files look like, and a dump that differed only in its final byte
        // would fail the check for no reason a reviewer could see.
        appendLine()
    }

    /**
     * Orders the members of a class the way a dump lists them.
     *
     * Fields come first as a group, then methods; within each group the order is by name
     * and then by descriptor, which is what disambiguates overloads such as a bridge
     * method and the method it delegates to.
     *
     * @param members The members in extraction order.
     * @return The members in dump order.
     * @since 2.1.0
     */
    private fun sortMembers(members: List<AbiMember>): List<AbiMember> =
        members.sortedWith(
            compareBy({ it.kind != AbiMemberKind.FIELD }, { it.name }, { it.descriptor })
        )

    /**
     * Renders one member line without its leading tab.
     *
     * @param member The member to render.
     * @return The rendered line.
     * @since 2.1.0
     */
    private fun renderMember(member: AbiMember): String =
        "${modifiers(member.access)} ${member.kind.keyword} ${member.name} ${member.descriptor}"

    /**
     * Renders the modifier prefix of a class or member declaration.
     *
     * The order is fixed — visibility, `static`, `final`, `abstract`, `synthetic` — because
     * a dump is compared as text, so a reordering would read as an API change.
     *
     * @param access The ASM access flags.
     * @return The space separated modifiers, always starting with a visibility.
     * @since 2.1.0
     */
    private fun modifiers(access: Int): String = buildList {
        add(visibilityOf(access))
        if (access and Opcodes.ACC_STATIC != 0) add("static")
        if (access and Opcodes.ACC_FINAL != 0) add("final")
        if (access and Opcodes.ACC_ABSTRACT != 0) add("abstract")
        if (access and Opcodes.ACC_SYNTHETIC != 0) add("synthetic")
    }.joinToString(" ")

    /**
     * Maps the access flags to a visibility keyword.
     *
     * Only `public` and `protected` ever reach the renderer, because anything else is
     * dropped during extraction; the `private` branch exists so that a filtering mistake
     * shows up as a visible diff rather than as a silently mislabelled declaration.
     *
     * @param access The ASM access flags.
     * @return The visibility keyword.
     * @since 2.1.0
     */
    private fun visibilityOf(access: Int): String = when {
        access and Opcodes.ACC_PUBLIC != 0 -> "public"
        access and Opcodes.ACC_PROTECTED != 0 -> "protected"
        access and Opcodes.ACC_PRIVATE != 0 -> "private"
        else -> "packageprivate"
    }
}
