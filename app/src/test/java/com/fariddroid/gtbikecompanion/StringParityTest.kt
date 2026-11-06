package com.fariddroid.gtbikecompanion

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** EN (default) and ES must have the same keys, placeholders and plural names (plan §11). */
class StringParityTest {
    private val res = File("src/main/res")

    private data class Entry(val name: String, val placeholders: List<String>)

    private fun load(dir: String): Map<String, Entry> {
        val out = mutableMapOf<String, Entry>()
        File(res, dir).listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }!!.forEach { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            listOf("string", "plurals").forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val e = nodes.item(i) as Element
                    if (e.getAttribute("translatable") == "false") continue
                    val name = "$tag:${e.getAttribute("name")}"
                    val formatted = e.getAttribute("formatted") != "false"
                    val text = e.textContent
                    val placeholders = if (formatted) Regex("%(\\d+\\$)?[sd]").findAll(text).map { it.value }.toSortedSet().toList() else emptyList()
                    out[name] = Entry(name, placeholders)
                }
            }
        }
        return out
    }

    @Test fun spanishHasExactlyTheEnglishKeys() {
        val en = load("values")
        val es = load("values-es")
        assertTrue("no strings found; working dir is ${File(".").absolutePath}", en.isNotEmpty())
        assertEquals("missing in values-es", emptySet<String>(), en.keys - es.keys)
        assertEquals("extra in values-es", emptySet<String>(), es.keys - en.keys)
    }

    @Test fun placeholdersMatch() {
        val en = load("values")
        val es = load("values-es")
        en.forEach { (name, entry) -> assertEquals(name, entry.placeholders, es.getValue(name).placeholders) }
    }

    @Test fun everyHelpTopicHasTitleAndBodyInBothLanguages() {
        val en = load("values").keys
        val es = load("values-es").keys
        val ids = Regex("""^\s+([A-Z_]+)\("([a-zA-Z.]+)"""", RegexOption.MULTILINE)
            .findAll(File("src/main/java/com/fariddroid/gtbikecompanion/ui/help/HelpTopic.kt").readText())
            .map { it.groupValues[2] }.toList()
        assertTrue(ids.size >= 60)
        ids.forEach { id ->
            val base = "string:help_" + id.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").replace('.', '_').lowercase()
            listOf("_title", "_body").forEach { suffix ->
                assertTrue("$base$suffix EN", (base + suffix) in en)
                assertTrue("$base$suffix ES", (base + suffix) in es)
            }
        }
    }
}
