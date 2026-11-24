package com.dicren.autospot.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the rule that every user-visible string exists in all nine locales.
 *
 * Missing translations silently fall back to English, which is easy to miss in review but shows
 * up as a half-translated screen for users. This check runs in the normal unit-test task, so CI
 * fails before such a change is merged.
 */
class LocaleResourcesTest {
    @Test
    fun `every locale defines the same strings and plurals as the base locale`() {
        val resDir = resourcesDir()
        val locales = localeFiles(resDir)
        assertTrue("Expected the base locale and its translations", locales.size >= 9)

        val base = names(File(resDir, "values/strings.xml"))
        assertTrue("The base locale should define strings", base.isNotEmpty())

        locales.forEach { file ->
            assertEquals(
                "Translations in ${file.parentFile.name} do not match values/strings.xml",
                base,
                names(file),
            )
        }
    }

    @Test
    fun `no locale defines a name twice`() {
        localeFiles(resourcesDir()).forEach { file ->
            val all = allNames(file)
            assertEquals(
                "Duplicated names in ${file.parentFile.name}/strings.xml",
                all.size,
                all.toSet().size,
            )
        }
    }

    private fun localeFiles(resDir: File): List<File> =
        resDir
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
            .sortedBy { it.parentFile.name }

    private fun names(file: File): Set<String> = allNames(file).toSet()

    private fun allNames(file: File): List<String> {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = false }
                .newDocumentBuilder()
                .parse(file)
        val names = mutableListOf<String>()
        listOf("string", "plurals").forEach { tag ->
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                if (element.hasAttribute("name")) names += element.getAttribute("name")
            }
        }
        return names
    }

    /** Unit tests run from the module directory; walk up until the resource tree is found. */
    private fun resourcesDir(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            listOf("app/src/main/res", "src/main/res").forEach { relative ->
                val candidate = File(dir, relative)
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        error("Could not locate src/main/res from ${System.getProperty("user.dir")}")
    }
}
