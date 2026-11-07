package com.example.robottirabasura.domain.planning

import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.SiteMap
import java.util.PriorityQueue
import kotlin.math.abs

/** A* over the 4-connected grid with Manhattan distance: shortest route that avoids every blocked cell. */
object PathPlanner {

    /** Route from [start] to [goal], both included, or null when the goal can't be reached. */
    fun findPath(map: SiteMap, start: Cell, goal: Cell, extraBlocked: Set<Cell> = emptySet()): List<Cell>? {
        if (!map.inBounds(start) || !map.isFree(goal, extraBlocked)) return null
        if (start == goal) return listOf(start)

        val cameFrom = HashMap<Cell, Cell>()
        val cost = HashMap<Cell, Int>().apply { put(start, 0) }
        // Ties are broken by insertion order so the same inputs always give the same route.
        var order = 0L
        val open = PriorityQueue<Triple<Int, Long, Cell>>(compareBy({ it.first }, { it.second }))
        open += Triple(heuristic(start, goal), order++, start)

        while (open.isNotEmpty()) {
            val current = open.poll()!!.third
            if (current == goal) return rebuild(cameFrom, goal)
            val nextCost = cost.getValue(current) + 1
            for (next in current.neighbors()) {
                if (!map.isFree(next, extraBlocked)) continue
                if (nextCost < (cost[next] ?: Int.MAX_VALUE)) {
                    cost[next] = nextCost
                    cameFrom[next] = current
                    open += Triple(nextCost + heuristic(next, goal), order++, next)
                }
            }
        }
        return null
    }

    private fun heuristic(a: Cell, b: Cell) = abs(a.x - b.x) + abs(a.y - b.y)

    private fun rebuild(cameFrom: Map<Cell, Cell>, goal: Cell): List<Cell> {
        val path = ArrayDeque<Cell>().apply { addFirst(goal) }
        var cell = goal
        while (true) {
            cell = cameFrom[cell] ?: break
            path.addFirst(cell)
        }
        return path.toList()
    }
}
