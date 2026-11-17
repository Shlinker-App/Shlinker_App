package app.shlinker.core.model

enum class ShortUrlSort(val orderBy: String) {
    NEWEST("dateCreated-DESC"),
    OLDEST("dateCreated-ASC"),
    MOST_VISITED("visits-DESC"),
    LEAST_VISITED("visits-ASC"),
}
