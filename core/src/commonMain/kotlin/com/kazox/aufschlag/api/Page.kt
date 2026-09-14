package com.kazox.aufschlag.api

import kotlinx.serialization.Serializable

/**
 * Response shape for every list endpoint. Requests page via `?limit=&cursor=`;
 * [nextCursor] is opaque to clients and null on the last page.
 */
@Serializable
data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)
