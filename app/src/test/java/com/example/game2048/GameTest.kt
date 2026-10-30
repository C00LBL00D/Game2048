package com.example.game2048

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameTest {

    private fun tile(id: Long, value: Int, row: Int, col: Int) = Game2048.Tile(id, value, row, col)

    private fun values(tiles: List<Game2048.Tile>): List<Int> =
        (0 until Game2048.N).map { cell -> tiles.firstOrNull { it.cell == cell }?.value ?: 0 }

    @Test
    fun mergeLine_mergesOncePerMove() {
        assertEquals(listOf(4, 4, 0, 0) to 8, Game2048.mergeLine(listOf(2, 2, 2, 2)))
        assertEquals(listOf(8, 4, 0, 0) to 8, Game2048.mergeLine(listOf(4, 4, 4, 0)))
        assertEquals(listOf(2, 0, 0, 0) to 0, Game2048.mergeLine(listOf(0, 0, 2, 0)))
    }

    @Test
    fun collapse_reportsWhichCellsWereMerged() {
        val (out, score, sources) = Game2048.collapse(listOf(2, 2, 4, 0))
        assertEquals(listOf(4, 4, 0, 0), out)
        assertEquals(4, score)
        assertEquals(listOf(listOf(0, 1), listOf(2), emptyList(), emptyList()), sources)
    }

    @Test
    fun moveLeft_packsAndMerges() {
        val board = listOf(
            tile(1, 2, 0, 0), tile(2, 2, 0, 2),
            tile(3, 4, 2, 0), tile(4, 4, 2, 1), tile(5, 4, 2, 2), tile(6, 4, 2, 3),
        )
        val result = Game2048.move(board, Game2048.LEFT)
        assertTrue(result.changed)
        assertEquals(4 + 16, result.score)
        assertEquals(
            listOf(
                4, 0, 0, 0,
                0, 0, 0, 0,
                8, 8, 0, 0,
                0, 0, 0, 0,
            ),
            values(result.tiles),
        )
    }

    @Test
    fun moveRight_isMirrorOfLeft() {
        val board = listOf(tile(1, 2, 0, 0), tile(2, 2, 0, 1))
        val result = Game2048.move(board, Game2048.RIGHT)
        assertTrue(result.changed)
        assertEquals(listOf(0, 0, 0, 4) + List(12) { 0 }, values(result.tiles))
    }

    @Test
    fun moveUp_worksOnColumns() {
        val board = listOf(
            tile(1, 2, 0, 0), tile(2, 2, 1, 0),
            tile(3, 4, 2, 0), tile(4, 4, 3, 0),
        )
        val result = Game2048.move(board, Game2048.UP)
        assertTrue(result.changed)
        assertEquals(4 + 8, result.score)
        assertEquals(listOf(4, 0, 0, 0, 8, 0, 0, 0) + List(8) { 0 }, values(result.tiles))
    }

    @Test
    fun merge_keepsOneTileWithNewIdAndSlidesBothSources() {
        val board = listOf(tile(7, 2, 0, 0), tile(8, 2, 0, 1))
        val result = Game2048.move(board, Game2048.LEFT)

        assertEquals(1, result.tiles.size)
        val merged = result.tiles.single()
        assertEquals(4, merged.value)
        assertEquals(0, merged.cell)
        assertNotEquals(7L, merged.id)
        assertNotEquals(8L, merged.id)

        assertEquals(2, result.slides.size)
        assertTrue(result.slides.all { it.merged && it.col == 0 })
    }

    @Test
    fun plainMove_keepsTileIdSoUiCanAnimateIt() {
        val board = listOf(tile(7, 8, 0, 3))
        val result = Game2048.move(board, Game2048.LEFT)
        assertEquals(7L, result.tiles.single().id)
        assertEquals(0, result.tiles.single().col)
        assertEquals(0, result.slides.single().col)
        assertFalse(result.slides.single().merged)
    }

    @Test
    fun move_returnsUnchangedBoardWhenNothingCanMove() {
        val full = listOf(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2,
        ).mapIndexed { i, v -> tile(i.toLong(), v, i / 4, i % 4) }

        val result = Game2048.move(full, Game2048.LEFT)
        assertFalse(result.changed)
        assertEquals(0, result.score)
        assertEquals(full, result.tiles)
    }

    @Test
    fun canMove_falseOnlyWhenBoardIsStuck() {
        val stuck = listOf(
            tile(1, 2, 0, 0), tile(2, 4, 0, 1), tile(3, 2, 0, 2), tile(4, 4, 0, 3),
            tile(5, 4, 1, 0), tile(6, 2, 1, 1), tile(7, 4, 1, 2), tile(8, 2, 1, 3),
            tile(9, 2, 2, 0), tile(10, 4, 2, 1), tile(11, 2, 2, 2), tile(12, 4, 2, 3),
            tile(13, 4, 3, 0), tile(14, 2, 3, 1), tile(15, 4, 3, 2), tile(16, 2, 3, 3),
        )
        assertFalse(Game2048.canMove(stuck))
        assertTrue(Game2048.canMove(stuck.drop(1)))
    }

    @Test
    fun spawn_fillsOneEmptyCellWithTwoOrFour() {
        val spawned = Game2048.spawn(emptyList(), Random(1))
        assertEquals(1, spawned.size)
        assertTrue(spawned.single().value == 2 || spawned.single().value == 4)

        val full = (0 until Game2048.N).map { tile(it.toLong() + 1, 2, it / 4, it % 4) }
        assertEquals(full, Game2048.spawn(full, Random(1)))
    }

    @Test
    fun move_reportsCreatedTilesForItemReward() {
        val board = listOf(tile(1, 1024, 1, 0), tile(2, 1024, 1, 1))
        val result = Game2048.move(board, Game2048.LEFT)
        assertEquals(2048, result.tiles.single().value)
        assertEquals(listOf(2048), result.created)

        val bigger = Game2048.move(listOf(tile(1, 2048, 1, 0), tile(2, 2048, 1, 1)), Game2048.LEFT)
        assertEquals(listOf(4096), bigger.created)

        val noMerge = Game2048.move(listOf(tile(1, 1024, 1, 0)), Game2048.LEFT)
        assertTrue(noMerge.created.isEmpty())
    }

    @Test
    fun dropCount_doublesEveryTier() {
        val random = Random(11)
        repeat(300) {
            assertTrue(Game2048.dropCount(2048, random) in 1..2)
            assertTrue(Game2048.dropCount(4096, random) in 2..4)
            assertTrue(Game2048.dropCount(8192, random) in 4..8)
            assertTrue(Game2048.dropCount(16384, random) in 8..16)
        }
        assertEquals(0, Game2048.dropCount(1024, random))
    }

    @Test
    fun shuffle_scattersTilesOverTheWholeBoard() {
        // 8 个棋子挤在首尾两行，重排后数量不变，但会散到全部 16 格里
        val board = (0 until 8).map {
            tile(it.toLong() + 1, (it + 1) * 2, if (it < 4) 0 else 3, it % 4)
        }
        val shuffled = Game2048.shuffle(board, Random(7))
        assertEquals(8, shuffled.size)
        assertEquals(board.map { it.value }.sorted(), shuffled.map { it.value }.sorted())
        assertEquals(8, shuffled.map { it.cell }.distinct().size)
        assertTrue(shuffled.all { it.cell in 0 until Game2048.N })
        assertNotEquals(board.map { it.cell }, shuffled.map { it.cell })
        assertTrue(shuffled.any { it.row == 1 || it.row == 2 })
    }

    @Test
    fun purge_removesOnlyTwoAndFour() {
        val board = listOf(
            tile(1, 2, 0, 0), tile(2, 4, 0, 1), tile(3, 8, 0, 2), tile(4, 16, 0, 3),
        )
        assertEquals(listOf(8, 16), Game2048.purge(board).map { it.value })
    }

    @Test
    fun newGame_startsWithTwoTiles() {
        val board = Game2048.newGame(Random(3))
        assertEquals(2, board.size)
        assertTrue(board.all { it.value == 2 || it.value == 4 })
        assertNotEquals(board[0].cell, board[1].cell)
    }

    @Test
    fun hammer_removesOnlyTheTargetCell() {
        val board = listOf(tile(1, 2, 0, 0), tile(2, 8, 1, 1))
        val after = Game2048.hammer(board, cell = 5)
        assertEquals(listOf(2), after.map { it.value })
        // 空格子敲了也没事
        assertEquals(board, Game2048.hammer(board, cell = 15))
    }

    @Test
    fun double_doublesOnlyTheTargetTile() {
        val board = listOf(tile(1, 2, 0, 0), tile(2, 8, 1, 1))
        val after = Game2048.double(board, id = 2)
        assertEquals(listOf(2, 16), after.map { it.value })
        assertEquals(listOf(1L, 2L), after.map { it.id })
        assertEquals(board, Game2048.double(board, id = 99))
    }

    @Test
    fun spawn_fourProbability() {
        val random = kotlin.random.Random(7)
        var fours = 0
        repeat(2000) {
            val t = Game2048.spawn(emptyList(), random)
            if (t.first().value == 4) fours++
        }
        println("SPAWN4 ratio=" + fours / 2000.0)
        assertTrue("4 的比例应在 15%~25%，实际 ${fours / 2000.0}", fours in 300..500)
    }
}
