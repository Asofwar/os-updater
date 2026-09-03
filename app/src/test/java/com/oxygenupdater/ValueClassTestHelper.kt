package com.oxygenupdater

import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Helpers for [`@JvmInline`][JvmInline]` value class` tests */
object ValueClassTestHelper {

    inline fun <reified T> ensureUniqueValues(
        prefixClassNameInToStringCheck: Boolean = true,
    ) {
        val clazz = T::class.java
        val companion = clazz.declaredClasses.find { it.simpleName == "Companion" }
        assertNotNull(companion) // ensure Companion exists

        val valueFieldType = clazz.getDeclaredField("value").type
        assertNotNull(valueFieldType) // ensure it has a `value` constructor param

        val methods = companion.declaredMethods.mapNotNull {
            // We only care about `public final` getters in value classes
            if (it.modifiers and (Modifier.PUBLIC or Modifier.FINAL) == 0) return@mapNotNull null
            // Ignore other non-getter methods
            if (it.parameterCount != 0) return@mapNotNull null
            // Ignore methods that don't have the same type the constructor param
            if (it.returnType != valueFieldType) return@mapNotNull null

            it.isAccessible = true; it
        }

        val companionObj = companion.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val values = methods.map { it.invoke(companionObj)!! }

        // If empty, there's probably something wrong with our reflection code above
        assertTrue(values.isNotEmpty())
        // Ensure no value is repeated (could be due to a human copy-paste mistake)
        assertEquals(values.distinct().size, values.size)

        // Values are fine, now check their toString() representations
        val constructor = clazz.getDeclaredConstructor(valueFieldType).apply { isAccessible = true }
        // Ensure it's what we expect: "ClassName.FieldName" or "ClassName.value" if `value` is a String
        values.map { constructor.newInstance(it).toString() }.forEachIndexed { index, string ->
            val expected = if (valueFieldType == String::class.java) {
                values[index] as String
            } else {
                // Strip Kotlin's default "get" prefix for methods and the generated "-<random>" suffix
                methods[index].name.removePrefix("get").substringBefore('-')
            }

            assertEquals(if (prefixClassNameInToStringCheck) "${clazz.simpleName}.$expected" else expected, string)
        }
    }
}
