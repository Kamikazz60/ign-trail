package fr.igntrail.core.tracking

/**
 * Liste à laquelle on ne fait qu'ajouter, dont les instantanés ne coûtent rien : un
 * instantané partage le tableau et ne lit que les éléments déjà présents. Les ajouts
 * suivants écrivent au-delà de sa taille (ou dans un nouveau tableau après agrandissement),
 * sans jamais modifier ce qu'il voit. Un seul thread écrit ; les instantanés peuvent être
 * lus ailleurs une fois publiés (StateFlow).
 *
 * Évite de recopier tout le tracé à chaque position : 40 000 points pour 6 h à 2 mesures/s.
 */
class AppendOnlyList<T> {
    private var items = arrayOfNulls<Any>(INITIAL_CAPACITY)
    var size = 0
        private set

    fun add(item: T) {
        if (size == items.size) items = items.copyOf(size * 2)
        items[size++] = item
    }

    fun snapshot(): List<T> = Snapshot(items, size)

    private class Snapshot<T>(private val items: Array<Any?>, override val size: Int) : AbstractList<T>() {
        @Suppress("UNCHECKED_CAST")
        override fun get(index: Int): T {
            if (index !in 0 until size) throw IndexOutOfBoundsException("$index / $size")
            return items[index] as T
        }
    }

    companion object {
        private const val INITIAL_CAPACITY = 256
    }
}
