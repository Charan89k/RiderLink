package com.example.riderlink

object Config {
    // =========================================================================
    // Token server configuration
    // =========================================================================
    // The app holds no LiveKit credentials. It asks the token server below for a
    // short-lived, room-scoped access token, and the server replies with both the
    // token and the LiveKit URL to connect to. The API secret lives only in the
    // server's environment -- see server/README.md.
    //
    // Replace this with your own deployed Worker URL.

    const val DEFAULT_TOKEN_SERVER_URL = "https://riderlink-token.workers.dev"
}
