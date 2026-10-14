package fr.igntrail.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleInjectionTest {
    /** Copie réelle du style Plan IGN classique (13/10/2026), qui contient des clés en double. */
    private val classique = javaClass.classLoader!!.getResource("classique.json")!!.readText()

    private fun member(text: String, objStart: Int, key: String) =
        JsonScanner.members(text, objStart).first { it.key == key }

    private fun layerIds(text: String): List<Pair<String, String>> {
        val layers = member(text, text.indexOf('{'), "layers")
        return JsonScanner.elements(text, layers.valueStart).map { range ->
            val m = JsonScanner.members(text, range.first)
            fun v(k: String) = m.first { it.key == k }.let { text.substring(it.valueStart + 1, it.valueEnd - 1) }
            v("id") to v("type")
        }
    }

    @Test
    fun `ombrage ajouté sous la première couche de lignes`() {
        val patched = StyleInjection.patch(classique)
        val before = layerIds(classique)
        val after = layerIds(patched)
        assertEquals(before.size + 1, after.size)
        val index = after.indexOfFirst { it.first == IgnLayers.HILLSHADE_ID }
        assertEquals("raster", after[index].second)
        assertEquals("line", after[index + 1].second)
        assertEquals(before, after.filterNot { it.first == IgnLayers.HILLSHADE_ID })
    }

    @Test
    fun `le reste du style est conservé à l'octet près`() {
        fun layerTexts(text: String): List<String> {
            val layers = member(text, text.indexOf('{'), "layers")
            return JsonScanner.elements(text, layers.valueStart).map { text.substring(it) }
        }
        val patched = StyleInjection.patch(classique)
        // Toutes les couches d'origine, clés en double comprises, inchangées et dans le même ordre.
        assertEquals(layerTexts(classique), layerTexts(patched).filterNot { it.contains(IgnLayers.HILLSHADE_ID) })
        // Les autres membres de premier niveau (glyphes, pictogrammes…) sont identiques.
        fun others(text: String) = JsonScanner.members(text, text.indexOf('{'))
            .filter { it.key != "sources" && it.key != "layers" }
            .map { it.key to text.substring(it.valueStart, it.valueEnd) }
        assertEquals(others(classique), others(patched))
    }

    @Test
    fun `tuiles vectorielles plafonnées au zoom 16, sans TileJSON`() {
        val patched = StyleInjection.patch(classique)
        val sources = member(patched, patched.indexOf('{'), "sources")
        val plan = member(patched, sources.valueStart, "plan_ign")
        val text = patched.substring(plan.valueStart, plan.valueEnd)
        assertTrue(text.contains(""""maxzoom":16"""))
        assertTrue(!text.contains(""""url""""))
        assertTrue(text.contains("/{z}/{x}/{y}.pbf"))
        val hill = member(patched, sources.valueStart, IgnLayers.HILLSHADE_ID)
        assertTrue(patched.substring(hill.valueStart, hill.valueEnd).contains(IgnLayers.HILLSHADE_TILES))
    }

    @Test
    fun `injection idempotente`() {
        val once = StyleInjection.patch(classique)
        assertEquals(once, StyleInjection.patch(once))
    }

    @Test
    fun `lecteur JSON - chaînes avec accolades et guillemets échappés`() {
        val text = """{"a":"x{]\"y","b":[1,{"c":"}"}],"d":true}"""
        assertEquals(listOf("a", "b", "d"), JsonScanner.members(text, 0).map { it.key })
        val b = JsonScanner.members(text, 0)[1]
        assertEquals(2, JsonScanner.elements(text, b.valueStart).size)
    }

    @Test
    fun `styles ciblés`() {
        assertTrue(IgnLayers.isPlanIgnStyle(IgnLayers.PLAN_IGN_CLASSIQUE))
        assertTrue(IgnLayers.isPlanIgnStyle(IgnLayers.PLAN_IGN_STANDARD))
        assertTrue(!IgnLayers.isPlanIgnStyle("https://data.geopf.fr/tms/1.0.0/PLAN.IGN/metadata.json"))
    }
}
