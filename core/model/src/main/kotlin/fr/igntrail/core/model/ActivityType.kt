package fr.igntrail.core.model

/** Profils d'activité (§5.1). Les seuils de filtrage GPS en dépendent. */
enum class ActivityType(val label: String) {
    WALK("Marche"),
    HIKE("Randonnée"),
    RUN("Course à pied"),

    /** Toute autre activité (vélo, ski, raquettes…) : filtrage plus permissif. */
    OTHER("Autre"),
}
