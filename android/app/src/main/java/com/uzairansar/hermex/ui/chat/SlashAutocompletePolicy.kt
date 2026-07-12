package com.uzairansar.hermex.ui.chat

import java.util.Locale

enum class SlashAutocompleteSuggestionKind {
    BuiltInCommand,
    Skill,
    ServerCommand,
    Argument,
}

data class SlashAutocompleteSuggestion(
    val key: String,
    val label: String,
    val detail: String,
    val replacement: String,
    val kind: SlashAutocompleteSuggestionKind,
    val argumentHint: String? = null,
    val isSelected: Boolean = false,
)

data class SlashServerCommand(
    val name: String,
    val description: String? = null,
    val argumentHint: String? = null,
    val isMobileVisible: Boolean = true,
)

data class SlashSkillDefinition(
    val name: String?,
    val category: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
    val disabled: Boolean? = null,
)

data class SlashSkillSuggestion(
    val name: String,
    val slug: String,
    val category: String? = null,
    val description: String? = null,
)

object SlashSkillFormatter {
    fun slug(name: String): String {
        val normalized = name.trim().lowercase(Locale.ROOT)
        val slug = StringBuilder(normalized.length)
        var previousWasHyphen = false

        normalized.forEach { character ->
            val output = when {
                character.isWhitespace() || character == '_' -> '-'
                character in 'a'..'z' || character in '0'..'9' || character == '-' -> character
                else -> null
            }
            if (output == null) return@forEach
            if (output == '-') {
                if (slug.isEmpty() || previousWasHyphen) return@forEach
                previousWasHyphen = true
            } else {
                previousWasHyphen = false
            }
            slug.append(output)
        }

        return slug.toString().trimEnd('-')
    }

    fun suggestions(definitions: List<SlashSkillDefinition>): List<SlashSkillSuggestion> {
        val seenSlugs = mutableSetOf<String>()
        return definitions
            .asSequence()
            .filter { definition -> definition.enabled != false && definition.disabled != true }
            .mapNotNull { definition ->
                val name = definition.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val slug = slug(name).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                SlashSkillSuggestion(
                    name = name,
                    slug = slug,
                    category = definition.category.trimmedOrNull(),
                    description = definition.description.trimmedOrNull(),
                )
            }
            .filter { suggestion -> seenSlugs.add(suggestion.slug) }
            .sortedBy { suggestion -> suggestion.slug }
            .toList()
    }

    fun matching(query: String, suggestions: List<SlashSkillSuggestion>): List<SlashSkillSuggestion> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return suggestions
        return suggestions.filter { suggestion ->
            suggestion.slug.contains(normalizedQuery) ||
                suggestion.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                suggestion.category?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true ||
                suggestion.description?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true
        }
    }

    fun skill(name: String, suggestions: List<SlashSkillSuggestion>): SlashSkillSuggestion? {
        val requested = name.trim().lowercase(Locale.ROOT)
        if (requested.isEmpty()) return null
        return suggestions.firstOrNull { suggestion ->
            suggestion.slug == requested || suggestion.name.lowercase(Locale.ROOT) == requested
        }
    }

    private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

data class SlashAutocompleteContext(
    val modelIds: List<String> = emptyList(),
    val profileNames: List<String> = emptyList(),
    val reasoningEfforts: List<String> = emptyList(),
    val workspacePaths: List<String> = emptyList(),
    val skillSuggestions: List<SlashSkillSuggestion> = emptyList(),
    val serverCommands: List<SlashServerCommand> = emptyList(),
    val selectedModelId: String? = null,
    val selectedProfileName: String? = null,
    val selectedReasoningEffort: String? = null,
    val selectedWorkspacePath: String? = null,
)

data class SlashAutocompleteResult(
    val isVisible: Boolean,
    val suggestions: List<SlashAutocompleteSuggestion> = emptyList(),
    val emptyMessage: String? = null,
)

object SlashAutocompletePolicy {
    private enum class ArgumentSource {
        None,
        Models,
        Profiles,
        Reasoning,
        Workspaces,
        GoalActions,
    }

    private data class BuiltInCommand(
        val name: String,
        val description: String,
        val argumentHint: String? = null,
        val argumentSource: ArgumentSource = ArgumentSource.None,
    )

    private data class ParsedQuery(
        val commandName: String,
        val argumentQuery: String,
        val hasArgumentSeparator: Boolean,
    )

    fun evaluate(
        draft: String,
        context: SlashAutocompleteContext = SlashAutocompleteContext(),
    ): SlashAutocompleteResult {
        val parsed = parse(draft) ?: return SlashAutocompleteResult(isVisible = false)
        val command = builtInCommands.firstOrNull { it.name.equals(parsed.commandName, ignoreCase = true) }

        if (command != null && parsed.hasArgumentSeparator) {
            if (command.argumentSource == ArgumentSource.None) {
                return SlashAutocompleteResult(isVisible = false)
            }

            val options = argumentOptions(command.argumentSource, context)
                .distinctNonBlankIgnoringCase()
                .filter { parsed.argumentQuery.isBlank() || it.startsWith(parsed.argumentQuery, ignoreCase = true) }

            if (
                command.argumentSource == ArgumentSource.GoalActions &&
                parsed.argumentQuery.isNotBlank() &&
                options.isEmpty()
            ) {
                return SlashAutocompleteResult(isVisible = false)
            }

            return SlashAutocompleteResult(
                isVisible = true,
                suggestions = options.map { option ->
                    argumentSuggestion(
                        command = command,
                        value = option,
                        context = context,
                    )
                },
                emptyMessage = if (options.isEmpty()) {
                    "No matches for \"${parsed.argumentQuery}\""
                } else {
                    null
                },
            )
        }

        if (
            parsed.hasArgumentSeparator &&
            SlashSkillFormatter.skill(parsed.commandName, context.skillSuggestions) != null
        ) {
            return SlashAutocompleteResult(isVisible = false)
        }

        if (
            parsed.hasArgumentSeparator &&
            context.serverCommands.any { serverCommand ->
                serverCommand.isMobileVisible &&
                    serverCommand.name.trim().equals(parsed.commandName, ignoreCase = true)
            }
        ) {
            return SlashAutocompleteResult(isVisible = false)
        }

        val query = parsed.commandName.trim().lowercase(Locale.ROOT)
        val builtIns = builtInCommands
            .filter { commandItem ->
                query.isEmpty() ||
                    commandItem.name.lowercase(Locale.ROOT).startsWith(query) ||
                    commandItem.description.lowercase(Locale.ROOT).contains(query)
            }
            .map(::builtInSuggestion)

        val reservedBuiltInNames = builtInCommands
            .mapTo(mutableSetOf()) { it.name.lowercase(Locale.ROOT) }
        val skillSuggestions = SlashSkillFormatter.matching(query, context.skillSuggestions)
            .filterNot { suggestion -> suggestion.slug in reservedBuiltInNames }
            .map(::skillSuggestion)

        val seenNames = reservedBuiltInNames.apply {
            skillSuggestions.mapTo(this) { suggestion -> suggestion.label.drop(1).lowercase(Locale.ROOT) }
        }
        val serverSuggestions = buildList {
            context.serverCommands.forEach { serverCommand ->
                val name = serverCommand.name.trim()
                val normalizedName = name.lowercase(Locale.ROOT)
                if (
                    serverCommand.isMobileVisible &&
                    name.isNotEmpty() &&
                    normalizedName !in seenNames &&
                    (query.isEmpty() || normalizedName.startsWith(query))
                ) {
                    add(
                        SlashAutocompleteSuggestion(
                            key = "server:$normalizedName",
                            label = "/$name",
                            detail = serverCommand.description?.trim()?.takeIf(String::isNotEmpty) ?: "Agent command",
                            replacement = "/$name ",
                            kind = SlashAutocompleteSuggestionKind.ServerCommand,
                            argumentHint = serverCommand.argumentHint?.trim()?.takeIf(String::isNotEmpty),
                        ),
                    )
                    seenNames += normalizedName
                }
            }
        }

        val suggestions = builtIns + skillSuggestions + serverSuggestions
        return SlashAutocompleteResult(
            isVisible = true,
            suggestions = suggestions,
            emptyMessage = if (suggestions.isEmpty()) {
                "No commands or skills match \"${parsed.commandName}\""
            } else {
                null
            },
        )
    }

    fun workspaceArgumentQuery(draft: String): String? {
        val parsed = parse(draft) ?: return null
        return parsed.argumentQuery.takeIf {
            parsed.hasArgumentSeparator && parsed.commandName.equals("workspace", ignoreCase = true)
        }
    }

    private fun builtInSuggestion(command: BuiltInCommand): SlashAutocompleteSuggestion =
        SlashAutocompleteSuggestion(
            key = "built-in:${command.name}",
            label = "/${command.name}",
            detail = command.description,
            replacement = "/${command.name} ",
            kind = SlashAutocompleteSuggestionKind.BuiltInCommand,
            argumentHint = command.argumentHint,
        )

    private fun skillSuggestion(skill: SlashSkillSuggestion): SlashAutocompleteSuggestion =
        SlashAutocompleteSuggestion(
            key = "skill:${skill.slug}",
            label = "/${skill.slug}",
            detail = skill.description ?: "Skill",
            replacement = "/${skill.slug} ",
            kind = SlashAutocompleteSuggestionKind.Skill,
            argumentHint = skill.category,
        )

    private fun argumentSuggestion(
        command: BuiltInCommand,
        value: String,
        context: SlashAutocompleteContext,
    ): SlashAutocompleteSuggestion {
        val selectedValue = when (command.argumentSource) {
            ArgumentSource.Models -> context.selectedModelId
            ArgumentSource.Profiles -> context.selectedProfileName
            ArgumentSource.Reasoning -> context.selectedReasoningEffort
            ArgumentSource.Workspaces -> context.selectedWorkspacePath
            ArgumentSource.GoalActions,
            ArgumentSource.None,
            -> null
        }
        val sourceLabel = when (command.argumentSource) {
            ArgumentSource.Models -> "Model"
            ArgumentSource.Profiles -> "Profile"
            ArgumentSource.Reasoning -> "Reasoning"
            ArgumentSource.Workspaces -> "Workspace"
            ArgumentSource.GoalActions -> "Goal action"
            ArgumentSource.None -> "Argument"
        }
        val displayLabel = if (command.argumentSource == ArgumentSource.GoalActions) {
            "/${command.name} $value"
        } else {
            value
        }
        return SlashAutocompleteSuggestion(
            key = "argument:${command.name}:${value.lowercase(Locale.ROOT)}",
            label = displayLabel,
            detail = sourceLabel,
            replacement = "/${command.name} $value",
            kind = SlashAutocompleteSuggestionKind.Argument,
            isSelected = selectedValue?.equals(value, ignoreCase = true) == true,
        )
    }

    private fun argumentOptions(
        source: ArgumentSource,
        context: SlashAutocompleteContext,
    ): List<String> = when (source) {
        ArgumentSource.Models -> context.modelIds
        ArgumentSource.Profiles -> context.profileNames
        ArgumentSource.Reasoning -> context.reasoningEfforts
        ArgumentSource.Workspaces -> context.workspacePaths
        ArgumentSource.GoalActions -> goalActions
        ArgumentSource.None -> emptyList()
    }

    private fun parse(draft: String): ParsedQuery? {
        val leadingTrimmed = draft.trimStart()
        if (!leadingTrimmed.startsWith('/')) return null

        val query = leadingTrimmed.drop(1)
        val commandName = query.takeWhile { !it.isWhitespace() }
        val hasArgumentSeparator = query.length > commandName.length && query[commandName.length].isWhitespace()
        val argumentQuery = if (hasArgumentSeparator) {
            query.drop(commandName.length).trim()
        } else {
            ""
        }
        return ParsedQuery(
            commandName = commandName,
            argumentQuery = argumentQuery,
            hasArgumentSeparator = hasArgumentSeparator,
        )
    }

    private fun List<String>.distinctNonBlankIgnoringCase(): List<String> {
        val seen = mutableSetOf<String>()
        return mapNotNull { value ->
            val trimmed = value.trim()
            val key = trimmed.lowercase(Locale.ROOT)
            trimmed.takeIf { it.isNotEmpty() && seen.add(key) }
        }
    }

    private val goalActions = listOf("status", "pause", "resume", "clear")

    private val builtInCommands = listOf(
        BuiltInCommand("help", "Show available slash commands"),
        BuiltInCommand("clear", "Clear the current conversation"),
        BuiltInCommand("model", "Switch the active model", "model_name", ArgumentSource.Models),
        BuiltInCommand("profile", "Switch the active profile", "name", ArgumentSource.Profiles),
        BuiltInCommand("workspace", "Switch the active workspace", "path", ArgumentSource.Workspaces),
        BuiltInCommand("reasoning", "Set reasoning effort level", "level", ArgumentSource.Reasoning),
        BuiltInCommand("new", "Start a new session"),
        BuiltInCommand("stop", "Stop the current response"),
        BuiltInCommand("title", "Rename the current session", "name"),
        BuiltInCommand("personality", "Set the session personality", "name"),
        BuiltInCommand("skills", "Search available skills", "query"),
        BuiltInCommand("compress", "Compress session context", "focus topic"),
        BuiltInCommand("compact", "Alias for /compress", "focus topic"),
        BuiltInCommand("retry", "Retry the last turn"),
        BuiltInCommand("undo", "Undo the last exchange"),
        BuiltInCommand("branch", "Fork the conversation", "name"),
        BuiltInCommand("fork", "Alias for /branch", "name"),
        BuiltInCommand("queue", "Queue a message for the next turn", "message"),
        BuiltInCommand("steer", "Steer the active response", "message"),
        BuiltInCommand("interrupt", "Stop the response and send a new message", "message"),
        BuiltInCommand("status", "Show session status"),
        BuiltInCommand("goal", "Set or inspect a persistent goal", "[status|pause|resume|clear|text]", ArgumentSource.GoalActions),
        BuiltInCommand("btw", "Ask a side question", "question"),
        BuiltInCommand("background", "Run a parallel task", "prompt"),
        BuiltInCommand("bg", "Alias for /background", "prompt"),
    )
}
