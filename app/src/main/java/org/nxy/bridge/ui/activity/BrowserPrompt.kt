package org.nxy.bridge.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AlertPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ButtonPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptInstanceDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoSession.PromptDelegate.TextPrompt

internal sealed interface BrowserPromptState {
    val prompt: BasePrompt
    val result: GeckoResult<PromptResponse>

    data class Alert(
        override val prompt: AlertPrompt,
        override val result: GeckoResult<PromptResponse>
    ) : BrowserPromptState

    data class Button(
        override val prompt: ButtonPrompt,
        override val result: GeckoResult<PromptResponse>
    ) : BrowserPromptState

    data class TextInput(
        override val prompt: TextPrompt,
        override val result: GeckoResult<PromptResponse>
    ) : BrowserPromptState

    data class Selection(
        override val prompt: ChoicePrompt,
        override val result: GeckoResult<PromptResponse>
    ) : BrowserPromptState
}

internal class BrowserPromptDelegate(
    private val onStateChanged: (BrowserPromptState?) -> Unit
) : GeckoSession.PromptDelegate {

    private var currentState: BrowserPromptState? = null

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: AlertPrompt
    ): GeckoResult<PromptResponse> {
        return show(BrowserPromptState.Alert(prompt, GeckoResult()))
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: ButtonPrompt
    ): GeckoResult<PromptResponse> {
        return show(BrowserPromptState.Button(prompt, GeckoResult()))
    }

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: TextPrompt
    ): GeckoResult<PromptResponse> {
        return show(BrowserPromptState.TextInput(prompt, GeckoResult()))
    }

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: ChoicePrompt
    ): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        prompt.delegate = object : PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: BasePrompt) {
                dismissByResult(result)
            }

            override fun onPromptUpdate(prompt: BasePrompt) {
                updateChoice(result, prompt as ChoicePrompt)
            }
        }
        return show(BrowserPromptState.Selection(prompt, result))
    }

    fun complete(
        state: BrowserPromptState,
        response: () -> PromptResponse
    ) {
        if (currentState !== state) return

        if (!state.prompt.isComplete) {
            state.result.complete(response())
        }
        currentState = null
        onStateChanged(null)
    }

    fun dismiss(state: BrowserPromptState) {
        complete(state) { state.prompt.dismiss() }
    }

    fun dismissCurrent() {
        currentState?.let(::dismiss)
    }

    private fun show(state: BrowserPromptState): GeckoResult<PromptResponse> {
        dismissCurrent()
        currentState = state
        onStateChanged(state)
        return state.result
    }

    private fun dismissByResult(result: GeckoResult<PromptResponse>) {
        val state = currentState ?: return
        if (state.result !== result) return
        dismiss(state)
    }

    private fun updateChoice(
        result: GeckoResult<PromptResponse>,
        prompt: ChoicePrompt
    ) {
        val state = currentState as? BrowserPromptState.Selection ?: return
        if (state.result !== result) return

        val updatedState = BrowserPromptState.Selection(prompt, result)
        currentState = updatedState
        onStateChanged(updatedState)
    }
}

@Composable
internal fun BrowserPromptHost(
    state: BrowserPromptState?,
    delegate: BrowserPromptDelegate
) {
    when (state) {
        is BrowserPromptState.Alert -> AlertPromptDialog(state, delegate)
        is BrowserPromptState.Button -> ButtonPromptDialog(state, delegate)
        is BrowserPromptState.TextInput -> TextPromptDialog(state, delegate)
        is BrowserPromptState.Selection -> ChoicePromptDialog(state, delegate)
        null -> Unit
    }
}

@Composable
private fun AlertPromptDialog(
    state: BrowserPromptState.Alert,
    delegate: BrowserPromptDelegate
) {
    AlertDialog(
        onDismissRequest = { delegate.dismiss(state) },
        confirmButton = {
            TextButton(onClick = { delegate.dismiss(state) }) {
                Text("确定")
            }
        },
        title = { Text("提示") },
        text = { Text(state.prompt.message.orEmpty()) }
    )
}

@Composable
private fun ButtonPromptDialog(
    state: BrowserPromptState.Button,
    delegate: BrowserPromptDelegate
) {
    val confirmNegative = {
        delegate.complete(state) {
            state.prompt.confirm(ButtonPrompt.Type.NEGATIVE)
        }
    }

    AlertDialog(
        onDismissRequest = confirmNegative,
        confirmButton = {
            TextButton(
                onClick = {
                    delegate.complete(state) {
                        state.prompt.confirm(ButtonPrompt.Type.POSITIVE)
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = confirmNegative) {
                Text("取消")
            }
        },
        title = { Text("确认") },
        text = { Text(state.prompt.message.orEmpty()) }
    )
}

@Composable
private fun TextPromptDialog(
    state: BrowserPromptState.TextInput,
    delegate: BrowserPromptDelegate
) {
    var value by remember(state.prompt) {
        mutableStateOf(state.prompt.defaultValue.orEmpty())
    }

    AlertDialog(
        onDismissRequest = { delegate.dismiss(state) },
        confirmButton = {
            TextButton(
                onClick = {
                    delegate.complete(state) {
                        state.prompt.confirm(value)
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { delegate.dismiss(state) }) {
                Text("取消")
            }
        },
        title = { Text("输入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.prompt.message?.takeIf { it.isNotEmpty() }?.let { message ->
                    Text(message)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

private data class ChoiceRow(
    val choice: ChoicePrompt.Choice,
    val depth: Int
)

private data class MenuLevel(
    val title: String,
    val choices: Array<ChoicePrompt.Choice>
)

private fun flattenChoices(
    choices: Array<ChoicePrompt.Choice>,
    depth: Int = 0
): List<ChoiceRow> {
    return choices.flatMap { choice ->
        listOf(ChoiceRow(choice, depth)) +
            (choice.items?.let { flattenChoices(it, depth + 1) } ?: emptyList())
    }
}

private fun selectedChoiceIds(choices: Array<ChoicePrompt.Choice>): Set<String> {
    return flattenChoices(choices)
        .filter { it.choice.selected }
        .mapTo(mutableSetOf()) { it.choice.id }
}

@Composable
private fun ChoicePromptDialog(
    state: BrowserPromptState.Selection,
    delegate: BrowserPromptDelegate
) {
    val prompt = state.prompt
    var selectedIds by remember(prompt) {
        mutableStateOf(selectedChoiceIds(prompt.choices))
    }
    var menuLevels by remember(prompt) {
        mutableStateOf(
            listOf(MenuLevel("选择", prompt.choices))
        )
    }

    val isMenu = prompt.type == ChoicePrompt.Type.MENU
    val isMultiple = prompt.type == ChoicePrompt.Type.MULTIPLE
    val rows = if (isMenu) {
        menuLevels.last().choices.map { ChoiceRow(it, 0) }
    } else {
        flattenChoices(prompt.choices)
    }

    AlertDialog(
        onDismissRequest = { delegate.dismiss(state) },
        confirmButton = {
            if (isMultiple) {
                TextButton(
                    onClick = {
                        delegate.complete(state) {
                            prompt.confirm(selectedIds.toTypedArray())
                        }
                    }
                ) {
                    Text("确定")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { delegate.dismiss(state) }) {
                Text("取消")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isMenu && menuLevels.size > 1) {
                    IconButton(
                        onClick = { menuLevels = menuLevels.dropLast(1) }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回上一级"
                        )
                    }
                }
                Text(if (isMenu) menuLevels.last().title else "选择")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prompt.message?.takeIf { it.isNotEmpty() }?.let { message ->
                    Text(message)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    rows.forEach { row ->
                        ChoiceItem(
                            row = row,
                            type = prompt.type,
                            selected = row.choice.id in selectedIds,
                            onClick = {
                                val children = row.choice.items
                                when {
                                    isMenu && !children.isNullOrEmpty() -> {
                                        menuLevels = menuLevels + MenuLevel(
                                            row.choice.label,
                                            children
                                        )
                                    }

                                    isMultiple -> {
                                        selectedIds = if (row.choice.id in selectedIds) {
                                            selectedIds - row.choice.id
                                        } else {
                                            selectedIds + row.choice.id
                                        }
                                    }

                                    else -> {
                                        delegate.complete(state) {
                                            prompt.confirm(row.choice)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ChoiceItem(
    row: ChoiceRow,
    type: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val choice = row.choice
    if (choice.separator) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        return
    }

    val isGroup = !choice.items.isNullOrEmpty()
    val isSelectable = !choice.disabled &&
        (type == ChoicePrompt.Type.MENU || !isGroup)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (choice.disabled) 0.38f else 1f)
            .clickable(enabled = isSelectable, onClick = onClick)
            .padding(start = (row.depth * 24).dp, top = 8.dp, bottom = 8.dp)
    ) {
        when {
            isGroup && type != ChoicePrompt.Type.MENU -> Unit
            type == ChoicePrompt.Type.MULTIPLE -> Checkbox(
                checked = selected,
                onCheckedChange = null,
                enabled = !choice.disabled
            )

            else -> RadioButton(
                selected = selected,
                onClick = null,
                enabled = !choice.disabled
            )
        }

        Text(
            text = choice.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isGroup) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        if (isGroup && type == ChoicePrompt.Type.MENU) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "进入下一级"
            )
        }
    }
}