package com.uzairansar.hermex.ui.onboarding

object OnboardingFlowPolicy {
    const val PageCount = 5
    const val ConnectPageIndex = 4
    const val AgentPromptPageIndex = 2

    const val TailscalePlayStoreUri = "market://details?id=com.tailscale.ipn"
    const val TailscalePlayStoreFallbackUrl = "https://play.google.com/store/apps/details?id=com.tailscale.ipn"

    val AgentSetupPrompt: String = """
        Set up Hermes Web UI on this machine for access from my Android phone via Tailscale.

        Clone and install https://github.com/nesquena/hermes-webui - it is a Node.js web app. Install dependencies and start it on port 8787.
        Enable password authentication by setting the HERMES_WEBUI_PASSWORD environment variable. Generate a secure random password and save it - I will need it for the Android app.
        Install Tailscale on this machine. Search the web for the correct install method for this OS if you are unsure. Authenticate to my Tailscale account - if this requires opening a URL or an auth key, tell me exactly what to do.
        Make the WebUI reachable over Tailscale:
        - Try tailscale serve --bg 8787 first (gives HTTPS and a friendly hostname).
        - If Tailscale Serve is disabled on my tailnet, bind the server to 0.0.0.0 so it listens on the tailnet interface. Before doing this, confirm password auth is active - never expose an unauthenticated WebUI.
        Set up auto-start appropriate for this OS so the WebUI survives reboots.
        Verify it works: curl http://$(tailscale ip -4):8787/health should return a success response.
        Reply with:
        - The exact server URL I enter in Hermex
        - The password
        - Any setup steps I still need to do on my Android phone
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
