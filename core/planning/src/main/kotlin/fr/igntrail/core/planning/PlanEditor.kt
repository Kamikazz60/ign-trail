package fr.igntrail.core.planning

import fr.igntrail.core.model.GeoPoint

/**
 * Édition des points d'un parcours (§8.1) : ajout, déplacement, suppression,
 * réorganisation, inversion, avec Annuler et Rétablir.
 * Chaque opération produit une nouvelle liste ; l'historique est borné.
 */
class PlanEditor(private val maxHistory: Int = 100) {
    var points: List<PlanPoint> = emptyList()
        private set

    private val undoStack = ArrayDeque<List<PlanPoint>>()
    private val redoStack = ArrayDeque<List<PlanPoint>>()
    private var nextId = 1L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Ajoute un point à la fin : il devient la nouvelle arrivée. */
    fun add(position: GeoPoint): PlanPoint {
        val p = PlanPoint(nextId++, position)
        commit(points + p)
        return p
    }

    /** Insère un point à [index] (0 = nouveau départ). */
    fun insert(index: Int, position: GeoPoint): PlanPoint {
        val p = PlanPoint(nextId++, position)
        commit(points.toMutableList().apply { add(index.coerceIn(0, size), p) })
        return p
    }

    fun move(id: Long, position: GeoPoint) {
        if (points.none { it.id == id }) return
        commit(points.map { if (it.id == id) it.copy(position = position) else it })
    }

    fun delete(id: Long) {
        if (points.none { it.id == id }) return
        commit(points.filterNot { it.id == id })
    }

    /** Déplace un point d'un rang vers le départ (-1) ou vers l'arrivée (+1). */
    fun shift(id: Long, delta: Int) {
        val i = points.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in points.indices) return
        commit(points.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun reverse() {
        if (points.size < 2) return
        commit(points.reversed())
    }

    fun clear() {
        if (points.isEmpty()) return
        commit(emptyList())
    }

    /** Charge un parcours existant (sans historique). */
    fun load(positions: List<GeoPoint>) {
        points = positions.map { PlanPoint(nextId++, it) }
        undoStack.clear()
        redoStack.clear()
    }

    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(points)
        points = previous
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(points)
        points = next
        return true
    }

    private fun commit(newPoints: List<PlanPoint>) {
        undoStack.addLast(points)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
        points = newPoints
    }
}
