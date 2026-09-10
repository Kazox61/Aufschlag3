package com.kazox.aufschlag

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform