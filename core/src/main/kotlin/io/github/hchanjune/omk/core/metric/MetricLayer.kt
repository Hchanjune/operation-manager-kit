package io.github.hchanjune.omk.core.metric

enum class MetricLayer(val label: String) {
    ENTRY("ENT"),
    APPLICATION("APP"),
    DB("DB "),
    CACHE("CAC"),
    EXTERNAL("EXT")
}
