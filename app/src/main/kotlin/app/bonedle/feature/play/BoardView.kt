package app.bonedle.feature.play

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.bonedle.R
import app.bonedle.engine.game.Game
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.rules.RegionStatus
import app.bonedle.ui.design.DominoOrientation
import app.bonedle.ui.design.RegionTexture
import app.bonedle.ui.design.RuleChip
import app.bonedle.ui.design.drawDomino
import app.bonedle.ui.design.drawRegionTexture
import app.bonedle.ui.design.regionColorName
import app.bonedle.ui.design.ruleSpoken
import app.bonedle.ui.design.toChipState
import app.bonedle.ui.theme.BonedleColors
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Radii
import app.bonedle.ui.theme.Sizes
import kotlin.math.roundToInt

/** Animation state the board draws; owned by the screen, which plays the effects. */
@Stable
class BoardMotion {
    val pulse = Animatable(1f)
    var pulseRegions by mutableStateOf(emptySet<Int>())
    val snapScale = Animatable(1f)
    var snapTray by mutableStateOf<Int?>(null)
    val shake = Animatable(0f)
    var shakeTray by mutableStateOf<Int?>(null)
    val sweep = Animatable(0f)
}

/** Precomputed paths for one region at one geometry. */
private class RegionShape(val cells: Path, val outline: Path, val innerLines: Path, val bounds: Rect)

private fun regionShapes(puzzle: Puzzle, g: BoardGeometry): List<RegionShape> {
    val regionOf = puzzle.regionOfCells()
    val board = puzzle.board
    return puzzle.regions.mapIndexed { r, region ->
        val cells = Path()
        val outline = Path()
        val inner = Path()
        var bounds: Rect? = null
        for (c in region.cells) {
            val rect = g.cellRect(c)
            cells.addRect(rect)
            bounds = bounds?.let { Rect(minOf(it.left, rect.left), minOf(it.top, rect.top), maxOf(it.right, rect.right), maxOf(it.bottom, rect.bottom)) } ?: rect
            val edges = listOf(
                0 to (rect.topLeft to rect.topRight),
                1 to (rect.topRight to rect.bottomRight),
                2 to (rect.bottomLeft to rect.bottomRight),
                3 to (rect.topLeft to rect.bottomLeft),
            )
            for ((dir, segment) in edges) {
                val n = board.neighbor(c, dir)
                val same = n >= 0 && regionOf[n] == r
                if (!same) {
                    outline.moveTo(segment.first.x, segment.first.y)
                    outline.lineTo(segment.second.x, segment.second.y)
                } else if (dir == 1 || dir == 2) {
                    inner.moveTo(segment.first.x, segment.first.y)
                    inner.lineTo(segment.second.x, segment.second.y)
                }
            }
        }
        RegionShape(cells, outline, inner, bounds ?: Rect.Zero)
    }
}

/**
 * The board: cells, regions (fill, texture, outer outline; dashed when broken; pulsing when
 * satisfied), rule chips, placed dominoes, hint highlights and the drop preview. Taps select
 * cells; dragging a placed domino moves it or, dropped off the board, returns it to the tray.
 * Every cell is also a TalkBack node with a tap action, so the board is fully playable without
 * dragging.
 */
@Composable
fun BoardView(
    game: Game,
    geometry: BoardGeometry,
    drag: DragState,
    motion: BoardMotion,
    highlight: Set<Int>,
    interactive: Boolean,
    onTapCell: (Int) -> Unit,
    onDrop: (PlayIntent.Drop) -> Unit,
    onChipTap: (Int) -> Unit,
    onReturnToTray: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** False when the board is only a picture (the share card): no per-cell TalkBack nodes. */
    cellSemantics: Boolean = true,
) {
    val colors = BonedleTheme.colors
    val density = LocalDensity.current
    val puzzle = game.puzzle
    val shapes = remember(puzzle, geometry.cell) { regionShapes(puzzle, geometry) }
    val sizeDp = with(density) { androidx.compose.ui.unit.DpSize(geometry.size.width.toDp(), geometry.size.height.toDp()) }
    Box(
        modifier
            .size(sizeDp)
            .onGloballyPositioned {
                drag.boardOrigin = it.positionInRoot()
                drag.geometry = geometry
            }
            .pointerInput(interactive, geometry) {
                if (!interactive) return@pointerInput
                detectTapGestures { pos ->
                    val c = geometry.cellAt(pos)
                    if (c >= 0) onTapCell(c)
                }
            }
            .pointerInput(interactive, game, geometry) {
                if (!interactive) return@pointerInput
                detectDragGestures(
                    onDragStart = { pos ->
                        val c = geometry.cellAt(pos)
                        val owner = if (c >= 0) game.ownerOf(c) else -1
                        val p = if (owner >= 0) game.placementOf(owner) else null
                        if (p != null) {
                            val rect = geometry.placementRect(p)
                            val horizontal = puzzle.board.row(p.cellA) == puzzle.board.row(p.cellB)
                            drag.start(owner, true, horizontal, p.valueA, p.valueB, drag.boardOrigin + pos, pos - rect.topLeft)
                        }
                    },
                    onDrag = { change, amount ->
                        if (drag.active && drag.fromBoard) {
                            change.consume()
                            drag.pointer += amount
                        }
                    },
                    onDragEnd = {
                        val t = drag.trayIndex
                        if (t != null && drag.fromBoard) onDrop(PlayIntent.Drop(t, drag.target, drag.overBoard, fromBoard = true))
                        drag.end()
                    },
                    onDragCancel = { drag.end() },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawBoard(game, geometry, shapes, colors, motion, highlight, drag)
        }
        // Rule chips straddle the top edge of each region's first cell, clear of the spots below.
        val chipNudge = with(density) { 4.dp.toPx() }
        puzzle.regions.forEachIndexed { r, region ->
            val anchor = region.cells.minWith(compareBy({ puzzle.board.row(it) }, { puzzle.board.col(it) }))
            val rect = geometry.cellRect(anchor)
            RuleChip(
                rule = region.rule,
                slot = region.color,
                state = game.regionStatuses[r].toChipState(),
                compact = true,
                onClick = if (interactive) ({ onChipTap(r) }) else null,
                modifier = Modifier.layout { measurable, constraints ->
                    val chip = measurable.measure(constraints)
                    layout(chip.width, chip.height) {
                        chip.place((rect.left - chipNudge).roundToInt(), (rect.top - chip.height / 2f).roundToInt())
                    }
                },
            )
        }
        if (cellSemantics) CellSemantics(game, geometry, interactive, onTapCell, onReturnToTray)
    }
}

/** One invisible TalkBack node per cell: "Row 2, column 3, teal region, sum equals 12, empty". */
@Composable
private fun CellSemantics(game: Game, geometry: BoardGeometry, interactive: Boolean, onTapCell: (Int) -> Unit, onReturnToTray: (Int) -> Unit) {
    val puzzle = game.puzzle
    val regionOf = remember(puzzle) { puzzle.regionOfCells() }
    val density = LocalDensity.current
    val cellDp = with(density) { geometry.cell.toDp() }
    val noRegion = stringResource(R.string.cell_no_region)
    val empty = stringResource(R.string.cell_empty)
    val tapPlace = stringResource(R.string.cell_action_place)
    val tapRotate = stringResource(R.string.cell_action_rotate)
    val returnLabel = stringResource(R.string.cell_action_return)
    for (c in 0 until puzzle.board.cellCount) {
        val r = regionOf[c]
        val regionText = if (r >= 0) {
            stringResource(R.string.cell_region, regionColorName(puzzle.regions[r].color), ruleSpoken(puzzle.regions[r].rule))
        } else {
            noRegion
        }
        val owner = game.ownerOf(c)
        val contentText = if (owner >= 0) {
            val d = game.tray[owner]
            stringResource(R.string.cell_value, game.cellValues[c], d.low, d.high)
        } else {
            empty
        }
        val description = stringResource(R.string.cell_description, puzzle.board.row(c) + 1, puzzle.board.col(c) + 1, regionText, contentText)
        val rect = geometry.cellRect(c)
        Box(
            Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(cellDp)
                .semantics {
                    contentDescription = description
                    if (interactive) {
                        onClick(label = if (owner >= 0) tapRotate else tapPlace) {
                            onTapCell(c)
                            true
                        }
                        if (owner >= 0) {
                            customActions = listOf(CustomAccessibilityAction(returnLabel) { onReturnToTray(owner); true })
                        }
                    }
                },
        )
    }
}

private fun DrawScope.drawBoard(
    game: Game,
    g: BoardGeometry,
    shapes: List<RegionShape>,
    colors: BonedleColors,
    motion: BoardMotion,
    highlight: Set<Int>,
    drag: DragState,
) {
    val puzzle = game.puzzle
    val board = puzzle.board
    val regionOf = puzzle.regionOfCells()
    drawRoundRect(colors.boardBase, cornerRadius = CornerRadius(Radii.board.toPx()))
    val hairline = Sizes.hairline.toPx()

    // Cells.
    for (c in 0 until board.cellCount) {
        val rect = g.cellRect(c)
        drawRect(colors.cellFree, rect.topLeft, rect.size)
        if (regionOf[c] < 0) {
            drawRect(
                colors.cellBorder,
                rect.topLeft + Offset(hairline / 2, hairline / 2),
                Size(rect.width - hairline, rect.height - hairline),
                style = Stroke(hairline),
            )
        }
    }
    // Regions: fill, texture, inner grid lines.
    val ink = colors.textPrimary.copy(alpha = BonedleColors.TEXTURE_ALPHA)
    puzzle.regions.forEachIndexed { r, region ->
        val shape = shapes[r]
        clipPath(shape.cells) {
            drawRect(colors.regionFill(region.color), shape.bounds.topLeft, shape.bounds.size)
            // Anchored to the board origin, so the pattern runs seamlessly across a region's cells.
            drawRegionTexture(RegionTexture.forSlot(region.color), ink)
        }
        drawPath(shape.innerLines, BonedleColors.CellGridLine, style = Stroke(hairline))
    }
    // Outlines: solid 3dp on outer edges; dashed 2dp when the rule is broken; satisfied pulse.
    // Each outline is drawn at double width and clipped to its own cells, so it lies wholly
    // inside its region and two neighbouring regions show both colours side by side.
    puzzle.regions.forEachIndexed { r, region ->
        val shape = shapes[r]
        val color = colors.region(region.color)
        val broken = game.regionStatuses[r] == RegionStatus.BROKEN
        val stroke = if (broken) {
            Stroke(
                width = Sizes.regionOutlineWarning.toPx() * 2,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            )
        } else {
            Stroke(width = Sizes.regionOutline.toPx() * 2, cap = StrokeCap.Square)
        }
        val pulse = if (r in motion.pulseRegions) motion.pulse.value else 1f
        clipPath(shape.cells) {
            scale(pulse, pivot = shape.bounds.center) { drawPath(shape.outline, color, style = stroke) }
        }
    }
    // Hint highlight.
    for (c in highlight) {
        val rect = g.cellRect(c)
        drawRoundRect(colors.accent.copy(alpha = 0.22f), rect.topLeft, rect.size, CornerRadius(4.dp.toPx()))
        drawRoundRect(colors.accent, rect.topLeft + Offset(hairline, hairline), Size(rect.width - hairline * 2, rect.height - hairline * 2), CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
    }
    // Drop preview.
    val target = drag.target
    val dragging = drag.trayIndex
    if (target != null && dragging != null) {
        val ownA = game.ownerOf(target.cellA)
        val ownB = game.ownerOf(target.cellB)
        val free = (ownA < 0 || ownA == dragging) && (ownB < 0 || ownB == dragging)
        if (free) {
            val rect = g.placementRect(target)
            drawRoundRect(colors.accent.copy(alpha = 0.18f), rect.topLeft, rect.size, CornerRadius(g.cell * Radii.DOMINO_FRACTION))
            drawRoundRect(colors.accent, rect.topLeft, rect.size, CornerRadius(g.cell * Radii.DOMINO_FRACTION), style = Stroke(2.dp.toPx()))
        }
    }
    // Dominoes.
    val inset = g.cell * DOMINO_INSET
    for (t in game.tray.indices) {
        val p = game.placementOf(t) ?: continue
        if (drag.fromBoard && dragging == t) continue
        val rect = g.placementRect(p)
        val horizontal = board.row(p.cellA) == board.row(p.cellB)
        val scale = if (motion.snapTray == t) motion.snapScale.value else 1f
        val shakeX = if (motion.shakeTray == t) motion.shake.value.dp.toPx() else 0f
        translate(shakeX, 0f) {
            scale(scale, pivot = rect.center) {
                drawDomino(
                    rect.topLeft + Offset(inset, inset),
                    g.cell - inset * 2,
                    if (horizontal) DominoOrientation.Horizontal else DominoOrientation.Vertical,
                    p.valueA,
                    p.valueB,
                    colors,
                )
            }
        }
    }
    // Solved: an accent sweep across the board.
    val sweep = motion.sweep.value
    if (sweep > 0f && sweep < 1f) {
        val w = size.width
        val x = -w * 0.4f + sweep * w * 1.8f
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color.Transparent, colors.accent.copy(alpha = 0.45f), Color.Transparent),
                start = Offset(x - w * 0.3f, 0f),
                end = Offset(x + w * 0.3f, size.height),
            ),
            cornerRadius = CornerRadius(Radii.board.toPx()),
        )
    }
}

/** Gap between a domino and its cells' edges, as a share of the cell. */
private const val DOMINO_INSET = 0.05f
