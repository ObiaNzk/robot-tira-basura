package com.example.robottirabasura.domain.model

enum class QrKind { BOLSAS, TACHO }

/** A point of the mission read from a printed QR: where the bags wait or where the bin is. */
data class QrTarget(val kind: QrKind, val id: String, val cell: Cell)

sealed interface QrParseResult {
    data class Valid(val target: QrTarget) : QrParseResult
    /** Not one of our codes (a menu, a link): the scanner ignores it without bothering the operator. */
    data object NotOurs : QrParseResult
    data class Invalid(val reason: QrError) : QrParseResult
}

enum class QrError { MALFORMED, OUT_OF_MAP, ON_OBSTACLE }

/**
 * Printed codes look like `RTB:BOLSAS:B1:1,9` or `RTB:TACHO:T1:16,1`: prefix, kind, a short id and the grid cell.
 * This format is a proposal for the course work, not a Unitree protocol.
 */
object QrCodes {
    const val PREFIX = "RTB:"
    private val pattern = Regex("""^RTB:(BOLSAS|TACHO):([A-Za-z0-9_-]{1,12}):(\d{1,3}),(\d{1,3})$""")

    fun build(target: QrTarget): String = "$PREFIX${target.kind.name}:${target.id}:${target.cell.x},${target.cell.y}"

    fun parse(raw: String, map: SiteMap): QrParseResult {
        val text = raw.trim()
        if (!text.startsWith(PREFIX)) return QrParseResult.NotOurs
        val match = pattern.matchEntire(text) ?: return QrParseResult.Invalid(QrError.MALFORMED)
        val (kind, id, x, y) = match.destructured
        val cell = Cell(x.toInt(), y.toInt())
        return when {
            !map.inBounds(cell) -> QrParseResult.Invalid(QrError.OUT_OF_MAP)
            !map.isFree(cell) -> QrParseResult.Invalid(QrError.ON_OBSTACLE)
            else -> QrParseResult.Valid(QrTarget(QrKind.valueOf(kind), id, cell))
        }
    }

    /** Codes printed for the demo (also offered as "simular lectura" when the device has no camera). */
    val DEMO_CODES: List<String> = listOf(
        "RTB:BOLSAS:B1:1,9",
        "RTB:BOLSAS:B2:6,4",
        "RTB:TACHO:T1:16,1",
        "RTB:TACHO:T2:13,7",
    )
}
