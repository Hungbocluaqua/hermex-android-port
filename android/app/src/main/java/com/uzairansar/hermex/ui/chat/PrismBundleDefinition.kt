package com.uzairansar.hermex.ui.chat

import io.noties.prism4j.annotations.PrismBundle

@PrismBundle(
    include = [
        "c",
        "clike",
        "cpp",
        "csharp",
        "css",
        "dart",
        "git",
        "go",
        "java",
        "javascript",
        "json",
        "kotlin",
        "markdown",
        "markup",
        "python",
        "sql",
        "swift",
        "yaml",
    ],
    grammarLocatorClassName = ".HermexGrammarLocator",
)
internal class PrismBundleDefinition
