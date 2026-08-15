package com.gkssky.artvisionpro.repository

import com.gkssky.artvisionpro.model.ArtSession

/** Persistence boundary for future session save and load operations. */
interface SessionRepository {
    suspend fun getSessions(): List<ArtSession>
    suspend fun save(session: ArtSession)
}
