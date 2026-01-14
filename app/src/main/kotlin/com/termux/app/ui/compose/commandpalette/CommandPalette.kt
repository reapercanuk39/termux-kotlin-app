package com.termux.app.ui.compose.commandpalette

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Command palette item representing a command or action
 */
data class CommandPaletteItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val command: String? = null,
    val icon: ImageVector = Icons.Default.Terminal,
    val category: CommandCategory = CommandCategory.COMMAND
)

enum class CommandCategory {
    COMMAND,
    HISTORY,
    FILE,
    ACTION,
    SNIPPET
}

/**
 * Command Palette - VS Code-style fuzzy command search
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onCommandSelected: (CommandPaletteItem) -> Unit,
    recentCommands: List<String> = emptyList(),
    customCommands: List<CommandPaletteItem> = emptyList()
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Default commands
    val defaultCommands = remember {
        listOf(
            CommandPaletteItem(
                id = "pkg_update",
                title = "Update packages",
                subtitle = "pkg update && pkg upgrade",
                command = "pkg update && pkg upgrade -y",
                icon = Icons.Default.Code,
                category = CommandCategory.COMMAND
            ),
            CommandPaletteItem(
                id = "pkg_install",
                title = "Install package",
                subtitle = "pkg install <package>",
                command = "pkg install ",
                icon = Icons.Default.Code,
                category = CommandCategory.COMMAND
            ),
            CommandPaletteItem(
                id = "clear",
                title = "Clear screen",
                subtitle = "Clear terminal output",
                command = "clear",
                icon = Icons.Default.Terminal,
                category = CommandCategory.COMMAND
            ),
            CommandPaletteItem(
                id = "ls",
                title = "List files",
                subtitle = "Show directory contents",
                command = "ls -la",
                icon = Icons.Default.Folder,
                category = CommandCategory.COMMAND
            ),
            CommandPaletteItem(
                id = "copy_path",
                title = "Copy current path",
                subtitle = "Copy working directory to clipboard",
                command = "pwd | termux-clipboard-set",
                icon = Icons.Default.ContentCopy,
                category = CommandCategory.ACTION
            ),
            CommandPaletteItem(
                id = "storage",
                title = "Setup storage",
                subtitle = "Access shared storage",
                command = "termux-setup-storage",
                icon = Icons.Default.Folder,
                category = CommandCategory.COMMAND
            ),
            CommandPaletteItem(
                id = "python",
                title = "Start Python",
                subtitle = "Launch Python interpreter",
                command = "python3",
                icon = Icons.Default.Code,
                category = CommandCategory.COMMAND
            ),
            CommandPaletteItem(
                id = "node",
                title = "Start Node.js",
                subtitle = "Launch Node.js REPL",
                command = "node",
                icon = Icons.Default.Code,
                category = CommandCategory.COMMAND
            ),
            CommandPaletteItem(
                id = "ssh",
                title = "SSH connection",
                subtitle = "Connect to remote server",
                command = "ssh ",
                icon = Icons.Default.Terminal,
                category = CommandCategory.COMMAND
            ),
            CommandPaletteItem(
                id = "git_status",
                title = "Git status",
                subtitle = "Show repository status",
                command = "git status",
                icon = Icons.Default.Code,
                category = CommandCategory.COMMAND
            )
        )
    }

    // Convert recent commands to palette items
    val historyItems = remember(recentCommands) {
        recentCommands.take(10).mapIndexed { index, cmd ->
            CommandPaletteItem(
                id = "history_$index",
                title = cmd,
                subtitle = "Recent command",
                command = cmd,
                icon = Icons.Default.History,
                category = CommandCategory.HISTORY
            )
        }
    }

    // All commands combined
    val allCommands = remember(historyItems, customCommands) {
        historyItems + defaultCommands + customCommands
    }

    // Fuzzy search filter
    val filteredCommands = remember(searchQuery, allCommands) {
        if (searchQuery.isBlank()) {
            allCommands
        } else {
            allCommands.filter { item ->
                fuzzyMatch(searchQuery.lowercase(), item.title.lowercase()) ||
                    (item.command?.lowercase()?.contains(searchQuery.lowercase()) == true)
            }.sortedByDescending { item ->
                fuzzyScore(searchQuery.lowercase(), item.title.lowercase())
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Search commands...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Command list
            LazyColumn(
                modifier = Modifier.height(400.dp)
            ) {
                items(filteredCommands) { item ->
                    CommandPaletteItemRow(
                        item = item,
                        searchQuery = searchQuery,
                        onClick = {
                            onCommandSelected(item)
                            onDismiss()
                        }
                    )
                }

                if (filteredCommands.isEmpty()) {
                    item {
                        Text(
                            text = "No commands found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Request focus on search field
    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun CommandPaletteItemRow(
    item: CommandPaletteItem,
    searchQuery: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = when (item.category) {
                    CommandCategory.HISTORY -> MaterialTheme.colorScheme.tertiary
                    CommandCategory.ACTION -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Highlighted title
                Text(
                    text = highlightMatches(item.title, searchQuery),
                    style = MaterialTheme.typography.bodyLarge
                )

                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun highlightMatches(text: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }

    var currentIndex = 0
    val queryLower = query.lowercase()
    val textLower = text.lowercase()

    for (char in queryLower) {
        val index = textLower.indexOf(char, currentIndex)
        if (index >= 0) {
            // Append non-matching text
            append(text.substring(currentIndex, index))
            // Append matching character with highlight
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                append(text[index])
            }
            currentIndex = index + 1
        }
    }
    // Append remaining text
    if (currentIndex < text.length) {
        append(text.substring(currentIndex))
    }
}

/**
 * Simple fuzzy matching algorithm
 */
private fun fuzzyMatch(query: String, text: String): Boolean {
    var queryIndex = 0
    for (char in text) {
        if (queryIndex < query.length && char == query[queryIndex]) {
            queryIndex++
        }
    }
    return queryIndex == query.length
}

/**
 * Calculate fuzzy match score (higher is better)
 */
private fun fuzzyScore(query: String, text: String): Int {
    if (query.isEmpty()) return 0

    var score = 0
    var queryIndex = 0
    var consecutiveMatches = 0

    for ((index, char) in text.withIndex()) {
        if (queryIndex < query.length && char == query[queryIndex]) {
            // Bonus for consecutive matches
            score += 1 + consecutiveMatches * 2
            consecutiveMatches++

            // Bonus for matching at start
            if (index == queryIndex) score += 5

            queryIndex++
        } else {
            consecutiveMatches = 0
        }
    }

    // Penalty for length difference
    score -= (text.length - query.length) / 2

    return if (queryIndex == query.length) score else 0
}
