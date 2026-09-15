package com.stuf.remotemouse.ui.control.text

import android.graphics.Color as AndroidColor
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import com.stuf.remotemouse.R

@Composable
fun TextControlPanel(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSecondary.toArgb()
    val cursorTint = MaterialTheme.colorScheme.primary.toArgb()
    val hint = stringResource(R.string.text_input_placeholder)
    // Avoid feedback when applying server/sync updates into the EditText.
    val lastEmitted = remember { object { var value: String? = null } }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            factory = { context ->
                EditText(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    background = null
                    gravity = Gravity.CENTER
                    setTextColor(textColor)
                    setHintTextColor(hintColor)
                    setHint(hint)
                    textSize = 24f
                    typeface = ResourcesCompat.getFont(context, R.font.kelly_slab_regular)
                    setLineSpacing(0f, 1.1f)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    textCursorDrawable?.setTint(cursorTint)
                    highlightColor = AndroidColor.argb(
                        0x66,
                        AndroidColor.red(cursorTint),
                        AndroidColor.green(cursorTint),
                        AndroidColor.blue(cursorTint),
                    )
                    doAfterTextChanged { editable ->
                        val newText = editable?.toString().orEmpty()
                        if (newText != lastEmitted.value) {
                            lastEmitted.value = newText
                            onTextChange(newText)
                        }
                    }
                    post { requestFocus() }
                }
            },
            update = { editText ->
                editText.setTextColor(textColor)
                editText.setHintTextColor(hintColor)
                editText.hint = hint
                if (editText.text?.toString() != text) {
                    val cursor = editText.selectionStart.coerceIn(0, text.length)
                    editText.setText(text)
                    editText.setSelection(cursor.coerceAtMost(text.length))
                    lastEmitted.value = text
                }
            },
        )
    }
}
