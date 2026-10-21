package com.app.youtubego.presentation.theme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Umpan balik sentuh untuk SELURUH `clickable` biasa di aplikasi: elemen mengecil sedikit saat ditekan
 * lalu kembali dengan halus. Pengganti ripple (yang sengaja dimatikan karena berkilat putih).
 */
object PressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource)
    override fun hashCode(): Int = -7
    override fun equals(other: Any?): Boolean = other === this
}

private class PressNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val factor = Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> factor.animateTo(0.97f, tween(70))
                    is PressInteraction.Release, is PressInteraction.Cancel -> factor.animateTo(1f, tween(140))
                    else -> Unit
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        scale(scale = factor.value, pivot = center) { this@draw.drawContent() }
    }
}

/** Skala animasi (1f -> [pressedScale]) selama [source] ditekan; untuk komponen Material yang punya interactionSource sendiri. */
@Composable
fun pressScaleOf(source: InteractionSource, pressedScale: Float = 0.95f): Float {
    val pressed by source.collectIsPressedAsState()
    val value by animateFloatAsState(if (pressed) pressedScale else 1f, tween(if (pressed) 70 else 140), label = "pressScale")
    return value
}
