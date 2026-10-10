package app.bonedle.feature.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.bonedle.R
import app.bonedle.engine.game.Game
import app.bonedle.ui.design.DominoFace
import app.bonedle.ui.design.DominoOrientation
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing

private val TrayCell = 22.dp
private val ItemShape = RoundedCornerShape(12.dp)

/**
 * The dominoes still to place, wrapped into rows. Tap selects (then tap a cell to place);
 * drag picks the domino up. Each item is at least 48dp in both directions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrayView(
    game: Game,
    poses: Map<Int, Pose>,
    selected: Int?,
    drag: DragState,
    motion: BoardMotion,
    interactive: Boolean,
    onSelect: (Int) -> Unit,
    onDrop: (PlayIntent.Drop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val left = game.unplaced
    val description = stringResource(R.string.tray_description, left.size)
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        for (t in left) {
            val pose = poses[t] ?: continue
            TrayItem(
                trayIndex = t,
                pose = pose,
                selected = selected == t,
                dragging = drag.trayIndex == t,
                shakeDp = if (motion.shakeTray == t) motion.shake.value else 0f,
                interactive = interactive,
                onSelect = { onSelect(t) },
                onDragStart = { origin, pos ->
                    val cell = drag.geometry?.cell ?: return@TrayItem
                    val w = if (pose.isHorizontal) cell * 2 else cell
                    val h = if (pose.isHorizontal) cell else cell * 2
                    // The ghost floats just above the finger so the finger never hides it.
                    drag.start(t, false, pose.isHorizontal, pose.leadingValue, pose.trailingValue, origin + pos, Offset(w / 2, h / 2 + cell * 0.6f))
                },
                onDrag = { amount -> if (drag.trayIndex == t) drag.pointer += amount },
                onDragEnd = {
                    if (drag.trayIndex == t) onDrop(PlayIntent.Drop(t, drag.target, drag.overBoard, fromBoard = false))
                    drag.end()
                },
                onDragCancel = { drag.end() },
            )
        }
    }
}

@Composable
private fun TrayItem(
    trayIndex: Int,
    pose: Pose,
    selected: Boolean,
    dragging: Boolean,
    shakeDp: Float,
    interactive: Boolean,
    onSelect: () -> Unit,
    onDragStart: (origin: Offset, pos: Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val colors = BonedleTheme.colors
    var origin by remember { mutableStateOf(Offset.Zero) }
    val selectedText = stringResource(R.string.tray_selected)
    val selectLabel = stringResource(R.string.tray_action_select)
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 60.dp, minHeight = Sizes.touchTarget)
            .graphicsLayer {
                translationX = shakeDp.dp.toPx()
                alpha = if (dragging) 0.3f else 1f
            }
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(ItemShape)
            .background(if (selected) colors.accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(2.dp, if (selected) colors.accent else Color.Transparent, ItemShape)
            .clickable(enabled = interactive, role = Role.Button, onClickLabel = selectLabel, onClick = onSelect)
            .pointerInput(interactive, trayIndex, pose) {
                if (!interactive) return@pointerInput
                detectDragGestures(
                    onDragStart = { pos -> onDragStart(origin, pos) },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            }
            .semantics { if (selected) stateDescription = selectedText }
            .padding(Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        DominoFace(
            first = pose.leadingValue,
            second = pose.trailingValue,
            cellSize = TrayCell,
            orientation = if (pose.isHorizontal) DominoOrientation.Horizontal else DominoOrientation.Vertical,
        )
    }
}
