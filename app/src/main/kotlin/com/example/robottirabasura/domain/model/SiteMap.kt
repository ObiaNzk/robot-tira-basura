package com.example.robottirabasura.domain.model

/** A cell of the site's occupancy grid. `x` grows to the east, `y` to the south. */
data class Cell(val x: Int, val y: Int) {
    fun neighbors(): List<Cell> = listOf(Cell(x + 1, y), Cell(x - 1, y), Cell(x, y + 1), Cell(x, y - 1))
}

/**
 * Occupancy grid of the service area. Static obstacles come from the layout; obstacles that appear while the robot
 * works (a cart, a person standing still) are passed separately as `extraBlocked`.
 */
class SiteMap(
    val name: String,
    val width: Int,
    val height: Int,
    val cellSizeMeters: Double,
    val blocked: Set<Cell>,
    val safePoint: Cell,
) {
    fun inBounds(cell: Cell): Boolean = cell.x in 0 until width && cell.y in 0 until height

    fun isFree(cell: Cell, extraBlocked: Set<Cell> = emptySet()): Boolean =
        inBounds(cell) && cell !in blocked && cell !in extraBlocked

    /** The map back as ASCII rows (same format as [fromLayout]). */
    fun toLayout(): List<String> = (0 until height).map { y ->
        (0 until width).joinToString("") { x ->
            val c = Cell(x, y)
            when {
                c == safePoint -> "S"
                c in blocked -> "#"
                else -> "."
            }
        }
    }

    companion object {
        /**
         * Parses an ASCII layout: `#` obstacle, `.` free, `S` safe point (free). Every row must have the same length
         * and exactly one `S` must exist.
         */
        fun fromLayout(name: String, rows: List<String>, cellSizeMeters: Double): SiteMap {
            require(rows.isNotEmpty()) { "Empty layout" }
            val width = rows.first().length
            require(rows.all { it.length == width }) { "All rows must have the same width" }
            val blocked = mutableSetOf<Cell>()
            var safe: Cell? = null
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, char ->
                    when (char) {
                        '#' -> blocked += Cell(x, y)
                        'S' -> {
                            require(safe == null) { "Only one safe point allowed" }
                            safe = Cell(x, y)
                        }
                        '.' -> Unit
                        else -> throw IllegalArgumentException("Unknown layout character '$char'")
                    }
                }
            }
            return SiteMap(name, width, rows.size, cellSizeMeters, blocked, requireNotNull(safe) { "Missing safe point" })
        }

        /** Patio de comidas used for the demo: tables (`#`) and the robot's charging corner (`S`). */
        val DEMO: SiteMap = fromLayout(
            name = "Patio de comidas – planta baja",
            cellSizeMeters = 0.5,
            rows = listOf(
                "..................",
                ".S......##........",
                "........##....##..",
                "..###...##....##..",
                "..###.........##..",
                "..........###.....",
                "....##....###.....",
                "....##............",
                "..........##..###.",
                "..##......##..###.",
                "..................",
            ),
        )
    }
}
