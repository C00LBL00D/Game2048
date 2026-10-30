package com.example.game2048

import kotlin.random.Random

/**
 * 2048 引擎。棋盘是带 id 的 Tile 列表——UI 靠 id 认人才能做位移动画。
 * 不依赖 Android，单测直接调。
 */
object Game2048 {
    const val SIZE = 4
    const val N = SIZE * SIZE

    const val LEFT = 0
    const val RIGHT = 1
    const val UP = 2
    const val DOWN = 3

    data class Tile(val id: Long, val value: Int, val row: Int, val col: Int) {
        val cell: Int get() = row * SIZE + col
    }

    /** 一次移动里某个旧方块去了哪；merged=true 表示它被并进目标格（UI 画淡出动画）。 */
    data class Slide(val tile: Tile, val row: Int, val col: Int, val merged: Boolean)

    data class MoveResult(
        val tiles: List<Tile>,
        val slides: List<Slide>,
        val score: Int,
        val changed: Boolean,
        /** 这次合成出来的 >=2048 的数值，用来发道具奖励 */
        val created: List<Int>,
    )

    fun newGame(random: Random = Random.Default): List<Tile> =
        spawn(spawn(emptyList(), random), random)

    /** 一条线挤向索引起点，相邻同值只合一次。sources[i] = 并进第 i 格的输入下标。 */
    fun collapse(line: List<Int>): Triple<List<Int>, Int, List<List<Int>>> {
        val present = line.indices.filter { line[it] != 0 }
        val out = ArrayList<Int>(SIZE)
        val sources = ArrayList<List<Int>>(SIZE)
        var score = 0
        var k = 0
        while (k < present.size) {
            val here = present[k]
            if (k + 1 < present.size && line[present[k + 1]] == line[here]) {
                val merged = line[here] * 2
                out += merged
                sources += listOf(here, present[k + 1])
                score += merged
                k += 2
            } else {
                out += line[here]
                sources += listOf(here)
                k++
            }
        }
        while (out.size < SIZE) {
            out += 0
            sources += emptyList<Int>()
        }
        return Triple(out, score, sources)
    }

    /** 只关心数值的旧接口，单测用。 */
    fun mergeLine(line: List<Int>): Pair<List<Int>, Int> =
        collapse(line).let { it.first to it.second }

    /** 四个方向各是 4 条线，线上索引顺序决定"挤向哪边"。 */
    private fun lines(dir: Int): List<IntArray> {
        val rows = (0 until SIZE).map { r -> IntArray(SIZE) { c -> r * SIZE + c } }
        val cols = (0 until SIZE).map { c -> IntArray(SIZE) { r -> r * SIZE + c } }
        return when (dir) {
            LEFT -> rows
            RIGHT -> rows.map { it.reversedArray() }
            UP -> cols
            else -> cols.map { it.reversedArray() }
        }
    }

    fun move(tiles: List<Tile>, dir: Int): MoveResult {
        val byCell = tiles.associateBy { it.cell }
        val values = IntArray(N) { byCell[it]?.value ?: 0 }
        var nextId = (tiles.maxOfOrNull { it.id } ?: 0L) + 1
        val out = ArrayList<Tile>(N)
        val slides = ArrayList<Slide>(N)
        var score = 0
        var changed = false
        val created = ArrayList<Int>()

        for (line in lines(dir)) {
            val before = line.map { values[it] }
            val (after, gained, sources) = collapse(before)
            score += gained
            if (after != before) changed = true
            line.forEachIndexed { outIdx, cell ->
                val from = sources[outIdx]
                if (from.isEmpty()) return@forEachIndexed
                val row = cell / SIZE
                val col = cell % SIZE
                val parts = from.map { byCell[line[it]]!! }
                parts.forEach { slides += Slide(it, row, col, merged = parts.size > 1) }
                val value = after[outIdx]
                if (value >= 2048 && parts.size > 1) created += value
                // 合出来的格子换新 id：UI 才会当成"新方块"弹一下；没合并的沿用旧 id 做位移
                val id = if (parts.size == 1) parts[0].id else nextId++
                out += Tile(id, value, row, col)
            }
        }
        return MoveResult(out, slides, score, changed, created)
    }

    /** 随机空位放新块：90% 是 2，10% 是 4。没有空位返回原样。 */
    fun spawn(tiles: List<Tile>, random: Random = Random.Default): List<Tile> {
        val occupied = tiles.mapTo(HashSet()) { it.cell }
        val free = (0 until N).filter { it !in occupied }
        if (free.isEmpty()) return tiles
        val cell = free.random(random)
        val id = (tiles.maxOfOrNull { it.id } ?: 0L) + 1
        return tiles + Tile(id, if (random.nextInt(5) == 0) 4 else 2, cell / SIZE, cell % SIZE)
    }

    fun canMove(tiles: List<Tile>): Boolean = (0..3).any { move(tiles, it).changed }

    /** 道具：棋子数量不变，位置在整个 16 格里重新随机。 */
    fun shuffle(tiles: List<Tile>, random: Random = Random.Default): List<Tile> {
        val cells = (0 until N).shuffled(random).take(tiles.size)
        return tiles.mapIndexed { i, t -> t.copy(row = cells[i] / SIZE, col = cells[i] % SIZE) }
    }

    /**
     * 道具掉落数量：合成 2048 掉 1~2 个，4096 掉 2~4 个，8192 掉 4~8 个，依次翻倍。
     */
    fun dropCount(value: Int, random: Random = Random.Default): Int {
        if (value < 2048) return 0
        val tier = (value / 2048).countTrailingZeroBits()
        val min = 1 shl tier
        return random.nextInt(min, min * 2 + 1)
    }

    /** 道具：清掉 2 和 4。 */
    fun purge(tiles: List<Tile>): List<Tile> = tiles.filter { it.value > 4 }

    /** 道具：敲掉指定格子。 */
    fun hammer(tiles: List<Tile>, cell: Int): List<Tile> = tiles.filter { it.cell != cell }

    /** 道具：指定方块数值翻倍。 */
    fun double(tiles: List<Tile>, id: Long): List<Tile> =
        tiles.map { if (it.id == id) it.copy(value = it.value * 2) else it }
}
