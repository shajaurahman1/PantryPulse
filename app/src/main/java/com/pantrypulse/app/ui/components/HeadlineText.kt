package com.pantrypulse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pantrypulse.app.ui.theme.PulseTheme

/** Big headline whose leading number sits in a rounded orange box, as in the design. */
@Composable
fun BoxedNumberHeadline(number: Int, text: String, modifier: Modifier = Modifier) {
    val c = PulseTheme.colors
    val digits = number.toString()
    val style = MaterialTheme.typography.displayMedium
    val inline = mapOf(
        "num" to InlineTextContent(
            Placeholder(width = (0.62 * digits.length + 0.42).em, height = 1.0.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                Text(digits, style = style, color = Color(0xFF17140F))
            }
        },
    )
    Text(
        buildAnnotatedString {
            appendInlineContent("num", digits)
            append(" ")
            append(text)
        },
        inlineContent = inline,
        style = style,
        color = c.ink,
        modifier = modifier.testTag("headline"),
    )
}
