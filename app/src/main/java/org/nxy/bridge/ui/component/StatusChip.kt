package org.nxy.bridge.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StatusChip(
    label: @Composable () -> Unit,
    containerColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    iconContentColor: Color = labelColor,
    shape: Shape = SuggestionChipDefaults.shape,
    border: BorderStroke? = SuggestionChipDefaults.suggestionChipBorder(enabled = true),
    minHeight: Dp = SuggestionChipDefaults.Height,
    horizontalArrangement: Arrangement.Horizontal = SuggestionChipDefaults.horizontalArrangement(),
    contentPadding: PaddingValues = SuggestionChipDefaults.ContentPadding,
    labelTextStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = containerColor,
        contentColor = labelColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        StaticChipContent(
            label = label,
            labelTextStyle = labelTextStyle,
            labelColor = labelColor,
            leadingIcon = icon,
            leadingIconColor = iconContentColor,
            minHeight = minHeight,
            horizontalArrangement = horizontalArrangement,
            paddingValues = contentPadding,
        )
    }
}

@Composable
private fun StaticChipContent(
    label: @Composable () -> Unit,
    labelTextStyle: TextStyle,
    labelColor: Color,
    leadingIcon: @Composable (() -> Unit)?,
    leadingIconColor: Color,
    minHeight: Dp,
    horizontalArrangement: Arrangement.Horizontal,
    paddingValues: PaddingValues,
) {
    CompositionLocalProvider(
        LocalContentColor provides labelColor,
        LocalTextStyle provides labelTextStyle,
    ) {
        Row(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .defaultMinSize(minHeight = minHeight)
                .padding(paddingValues),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = horizontalArrangement,
        ) {
            if (leadingIcon != null) {
                CompositionLocalProvider(
                    LocalContentColor provides leadingIconColor,
                    content = {
                        Box(contentAlignment = Alignment.Center) { leadingIcon() }
                    },
                )
            } else {
                Spacer(Modifier.width(0.dp))
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) { label() }

            // SuggestionChip 源码为了“对齐三段结构”会保留尾部占位，这里同样保留
            Spacer(Modifier.width(0.dp))
        }
    }
}