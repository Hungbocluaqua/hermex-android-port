package com.uzairansar.hermex.ui.onboarding

object OnboardingFlowPolicy {
    const val PageCount = 5
    const val ConnectPageIndex = 4
    const val AgentPromptPageIndex = 2

    const val TailscalePlayStoreUri = "market://details?id=com.tailscale.ipn"
    const val TailscalePlayStoreFallbackUrl = "https://play.google.com/store/apps/details?id=com.tailscale.ipn"

    val AgentSetupPrompt: String = """
        Set up Hermes Web UI on this machine for access from my Android phone via Tailscale.

        Hermes Web UI uses the Python standard library and vanilla JavaScript. Use only Python's standard library and the repository's existing frontend; do not add dependencies.

        Inventory before changing anything:
        - Locate any existing hermes-webui checkout, configuration, launcher, service, and running process. Reuse and preserve working state instead of reinstalling it.
        - Check who owns port 8787 with `lsof -nP -iTCP:8787 -sTCP:LISTEN` (or the OS equivalent). Do not kill an unknown process; stop and report the owner or conflict.
        - Run `command -v tailscale`, `tailscale version`, and `tailscale status`. If Tailscale is installed, do not reinstall it. Only if `command -v tailscale` reports that Tailscale is absent, install it using the correct method for this OS, then rerun `tailscale version`, `tailscale status`, and the authentication check before proceeding. If it is installed but not running or authenticated, explain the exact user action required.
        - Run `tailscale serve status` and `tailscale funnel status` before changing routes. Preserve every existing Serve and Funnel route. Do not run tailscale serve reset, remove routes, or overwrite an occupied HTTPS listener or path.

        Set up or repair Hermes Web UI safely:
        - Clone https://github.com/nesquena/hermes-webui only if no usable checkout exists. Inspect its README and use a supported launcher: `python3 bootstrap.py` or `./ctl.sh`.
        - Keep the WebUI bound to `127.0.0.1:8787`.
        - Preserve every existing line in `.env`; only add or update the `HERMES_WEBUI_PASSWORD` entry, and never truncate or replace the file. Preserve an existing password; if none exists, generate a secure random one. Set `umask 077` before creating a new `.env`. Whether `.env` already existed or is new, inspect its permissions and run `chmod 600 .env`. Do not print the full .env or expose unrelated secrets.
        - Reuse an existing service when present. Do not configure auto-start yourself. Propose the exact OS-appropriate commands and steps around the verified launcher, then wait for me to run them. Do not touch `~/Library/LaunchAgents/` or restart Mac services.

        Expose only the localhost service through private Tailscale HTTPS:
        - First confirm from `tailscale serve status` and `tailscale funnel status` that HTTPS port 443 at the root path is unused. Run `tailscale serve --bg 8787` only if HTTPS port 443 at the root path is free. Never enable Funnel.
        - If Tailscale requires HTTPS consent, show me the consent URL and explain the certificate-transparency disclosure before continuing.
        - If the root listener or route is already occupied, do not reset or replace it. Stop and report the exact conflict and safe options.

        Verify in this order:
        1. Confirm localhost health with `curl --fail http://127.0.0.1:8787/health`.
        2. Read back `tailscale serve status`, identify the actual ts.net HTTPS URL, and verify that exact URL's `/health` endpoint with `curl --fail https://<actual-ts.net-hostname>/health`.

        Treat binding to `0.0.0.0` or using a Tailscale IP over plain HTTP as an explicit manual fallback only. Explain the additional exposure and require my confirmation. Do not automate it.

        Reply with the exact HTTPS URL, password, launcher, and both health-check results, plus any remaining action required on my Android phone. Do not include the full `.env` contents.
        Do not use Cloudflare. Optimize for Tailscale and Android.
    """.trimIndent()

    fun primaryButtonTitle(page: Int): String =
        when (page) {
            0 -> "Get Started"
            1 -> "Set Up"
            ConnectPageIndex -> "Connect"
            else -> "Continue"
        }

    fun shouldShowCopyReminder(
        page: Int,
        hasCopiedAgentPrompt: Boolean,
        hasBypassedCopyReminder: Boolean = false,
    ): Boolean =
        page == AgentPromptPageIndex && !hasCopiedAgentPrompt && !hasBypassedCopyReminder

    fun shouldInterceptForwardNavigationFromAgentPrompt(
        oldPage: Int,
        newPage: Int,
        hasCopiedAgentPrompt: Boolean,
        hasBypassedCopyReminder: Boolean = false,
    ): Boolean =
        oldPage == AgentPromptPageIndex &&
            newPage > oldPage &&
            !hasCopiedAgentPrompt &&
            !hasBypassedCopyReminder

    fun showsServerShortcut(page: Int): Boolean = page < ConnectPageIndex
}
