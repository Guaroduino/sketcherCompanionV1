package com.sketcher.sketchercompanionv1.ui.dialogs

import android.graphics.Typeface
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sketcher.sketchercompanionv1.dto.TextEditState
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditDialog(
    state: TextEditState,
    theme: UiThemeConfig = UiThemeConfig(),
    onDismiss: () -> Unit,
    onConfirm: (html: String, textColor: Int, textSize: Float, fontFamily: String, alignment: String, template: String?) -> Unit
) {
    val scaler = LocalUiScaler.current
    val scaleFactor = scaler.scaleFactor

    // State parameters matching initial input
    var textColor by remember { mutableIntStateOf(state.defaultTextColor) }
    var textSize by remember { mutableFloatStateOf(state.defaultTextSize) }
    var fontFamilyName by remember { mutableStateOf(state.fontFamilyName) }
    var alignment by remember { mutableStateOf(state.alignment) }
    var styleTemplateName by remember { mutableStateOf(state.styleTemplateName) }

    // Reference to the native EditText
    var editTextRef by remember { mutableStateOf<EditText?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .padding(16.dp * scaleFactor),
            shape = RoundedCornerShape(16.dp * scaleFactor),
            color = theme.barBackgroundColor,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp * scaleFactor)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)
            ) {
                // Title
                Text(
                    text = if (state.isNewText) "Crear Cuadro de Texto" else "Editar Texto Enriquecido",
                    color = theme.iconColor,
                    fontSize = (18 * scaleFactor).sp,
                    style = MaterialTheme.typography.titleMedium
                )

                // Formatting Toolbar (Bold, Italic, Underline, Bullet List)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.barBackgroundColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp * scaleFactor))
                        .padding(4.dp * scaleFactor),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            editTextRef?.let { toggleStyleSpan(it, Typeface.BOLD) }
                        },
                        modifier = Modifier.size(36.dp * scaleFactor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatBold,
                            contentDescription = "Negrita",
                            tint = theme.iconColor,
                            modifier = Modifier.size(20.dp * scaleFactor)
                        )
                    }

                    IconButton(
                        onClick = {
                            editTextRef?.let { toggleStyleSpan(it, Typeface.ITALIC) }
                        },
                        modifier = Modifier.size(36.dp * scaleFactor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatItalic,
                            contentDescription = "Cursiva",
                            tint = theme.iconColor,
                            modifier = Modifier.size(20.dp * scaleFactor)
                        )
                    }

                    IconButton(
                        onClick = {
                            editTextRef?.let { toggleUnderlineSpan(it) }
                        },
                        modifier = Modifier.size(36.dp * scaleFactor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatUnderlined,
                            contentDescription = "Subrayado",
                            tint = theme.iconColor,
                            modifier = Modifier.size(20.dp * scaleFactor)
                        )
                    }

                    IconButton(
                        onClick = {
                            editTextRef?.let { toggleBulletSpan(it) }
                        },
                        modifier = Modifier.size(36.dp * scaleFactor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = "Viñetas",
                            tint = theme.iconColor,
                            modifier = Modifier.size(20.dp * scaleFactor)
                        )
                    }
                }

                // Native EditText Editor Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp * scaleFactor)
                        .border(1.dp, theme.highlightColor, RoundedCornerShape(8.dp * scaleFactor))
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp * scaleFactor))
                        .padding(8.dp * scaleFactor)
                ) {
                    AndroidView(
                        factory = { context ->
                            EditText(context).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setTextColor(theme.iconColor.hashCode()) // Use theme text color
                                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                // Load initial rich HTML text
                                if (state.textHtml.isNotEmpty()) {
                                    setText(Html.fromHtml(state.textHtml, Html.FROM_HTML_MODE_LEGACY))
                                }
                                editTextRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { edit ->
                            edit.setTextColor(theme.iconColor.hashCode())
                        }
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = theme.iconColor.copy(alpha = 0.6f))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            val editText = editTextRef
                            if (editText != null) {
                                // Serialize Spanned text to HTML
                                val html = Html.toHtml(editText.text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
                                onConfirm(html, textColor, textSize, fontFamilyName, alignment, styleTemplateName)
                            } else {
                                onConfirm("", textColor, textSize, fontFamilyName, alignment, styleTemplateName)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Guardar", color = Color.White)
                    }
                }
            }
        }
    }
}

// Rich Text helper methods to apply Span tags onto EditText selections
private fun toggleStyleSpan(editText: EditText, style: Int) {
    val start = editText.selectionStart
    val end = editText.selectionEnd
    if (start == -1 || end == -1 || start == end) return

    val spannable = editText.text
    val spans = spannable.getSpans(start, end, StyleSpan::class.java)
    var found = false
    for (span in spans) {
        if (span.style == style) {
            spannable.removeSpan(span)
            found = true
        }
    }
    if (!found) {
        spannable.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun toggleUnderlineSpan(editText: EditText) {
    val start = editText.selectionStart
    val end = editText.selectionEnd
    if (start == -1 || end == -1 || start == end) return

    val spannable = editText.text
    val spans = spannable.getSpans(start, end, UnderlineSpan::class.java)
    if (spans.isNotEmpty()) {
        for (span in spans) {
            spannable.removeSpan(span)
        }
    } else {
        spannable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun toggleBulletSpan(editText: EditText) {
    val start = editText.selectionStart
    val end = editText.selectionEnd
    if (start == -1 || end == -1) return

    val spannable = editText.text
    
    // Toggle BulletSpan on paragraphs in selection
    val textLength = spannable.length
    val selStart = start.coerceIn(0, textLength)
    val selEnd = end.coerceIn(0, textLength)

    // Find paragraph boundaries
    var pStart = selStart
    while (pStart > 0 && spannable[pStart - 1] != '\n') {
        pStart--
    }
    var pEnd = selEnd
    while (pEnd < textLength && spannable[pEnd] != '\n') {
        pEnd++
    }

    val spans = spannable.getSpans(pStart, pEnd, BulletSpan::class.java)
    if (spans.isNotEmpty()) {
        for (span in spans) {
            spannable.removeSpan(span)
        }
    } else {
        spannable.setSpan(BulletSpan(12), pStart, pEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
