package fr.igntrail.map

/**
 * Lecture structurelle minimale d'un texte JSON, sans le réécrire : permet de modifier
 * un style en ne touchant que les fragments voulus. Le style Plan IGN contient des
 * clés en double, qu'une analyse puis une réécriture complète risquerait d'altérer.
 */
internal object JsonScanner {
    data class Member(val key: String, val valueStart: Int, val valueEnd: Int)

    /** Membres de premier niveau de l'objet qui commence à [start] (caractère `{`). */
    fun members(text: String, start: Int): List<Member> {
        require(text[start] == '{') { "objet attendu à $start" }
        val result = mutableListOf<Member>()
        var i = skipSpaces(text, start + 1)
        while (text[i] != '}') {
            val keyEnd = endOfValue(text, i)
            val key = text.substring(i + 1, keyEnd - 1)
            i = skipSpaces(text, keyEnd)
            require(text[i] == ':') { "':' attendu à $i" }
            val valueStart = skipSpaces(text, i + 1)
            val valueEnd = endOfValue(text, valueStart)
            result += Member(key, valueStart, valueEnd)
            i = skipSpaces(text, valueEnd)
            if (text[i] == ',') i = skipSpaces(text, i + 1)
        }
        return result
    }

    /** Éléments de premier niveau du tableau qui commence à [start] (caractère `[`). */
    fun elements(text: String, start: Int): List<IntRange> {
        require(text[start] == '[') { "tableau attendu à $start" }
        val result = mutableListOf<IntRange>()
        var i = skipSpaces(text, start + 1)
        while (text[i] != ']') {
            val end = endOfValue(text, i)
            result += i until end
            i = skipSpaces(text, end)
            if (text[i] == ',') i = skipSpaces(text, i + 1)
        }
        return result
    }

    /** Indice qui suit la valeur commençant à [start] (objet, tableau, chaîne ou littéral). */
    fun endOfValue(text: String, start: Int): Int {
        when (text[start]) {
            '"' -> {
                var i = start + 1
                while (text[i] != '"') i += if (text[i] == '\\') 2 else 1
                return i + 1
            }
            '{', '[' -> {
                var depth = 0
                var i = start
                while (true) {
                    when (text[i]) {
                        '"' -> {
                            i = endOfValue(text, i)
                            continue
                        }
                        '{', '[' -> depth++
                        '}', ']' -> if (--depth == 0) return i + 1
                    }
                    i++
                }
            }
            else -> {
                var i = start
                while (i < text.length && text[i] !in ",}] \n\r\t") i++
                return i
            }
        }
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var i = from
        while (text[i].isWhitespace()) i++
        return i
    }
}
