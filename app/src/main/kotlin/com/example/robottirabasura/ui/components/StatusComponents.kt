package com.example.robottirabasura.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.robottirabasura.R
import com.example.robottirabasura.domain.rules.Availability
import com.example.robottirabasura.ui.theme.RobotThemeExtras

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val s = RobotThemeExtras.status
    return when (tone) {
        Tone.OK -> s.ok to s.onOk
        Tone.WARNING -> s.warning to s.onWarning
        Tone.ERROR -> s.error to s.onError
        Tone.NEUTRAL -> s.neutral to s.onNeutral
    }
}

/** A colored label. Color is never the only cue: the text always says the state. */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val (container, content) = toneColors(tone)
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun StaleBanner(ageSeconds: Long, modifier: Modifier = Modifier) {
    val (container, content) = toneColors(Tone.WARNING)
    Surface(color = container, contentColor = content, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WifiOff, contentDescription = null)
            Text(stringResource(R.string.stale_banner, ageSeconds), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

enum class CommandStyle { PRIMARY, TONAL, OUTLINED, DANGER }

/** A command button that explains why it's disabled, instead of just greying out. */
@Composable
fun CommandButton(
    text: String,
    availability: Availability,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CommandStyle = CommandStyle.TONAL,
    icon: ImageVector? = null,
) {
    val reason = availability.reason?.let { stringResource(it.label) }
    Column(modifier) {
        val content: @Composable () -> Unit = {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            }
            Text(text)
        }
        val buttonModifier = Modifier
            .fillMaxWidth()
            .semantics { if (reason != null) contentDescription = "$text. $reason" }
        when (style) {
            CommandStyle.PRIMARY -> Button(onClick, buttonModifier, enabled = availability.enabled) { content() }
            CommandStyle.TONAL -> FilledTonalButton(onClick, buttonModifier, enabled = availability.enabled) { content() }
            CommandStyle.OUTLINED -> OutlinedButton(onClick, buttonModifier, enabled = availability.enabled) { content() }
            CommandStyle.DANGER -> Button(
                onClick, buttonModifier, enabled = availability.enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { content() }
        }
        if (reason != null) {
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.confirm_yes),
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm_no)) } },
    )
}
