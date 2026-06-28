package com.wingedsheep.gameserver.persistence

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

/**
 * Spring Data JDBC repositories. These beans only exist when accounts are enabled (the JDBC
 * auto-config is @ConditionalOnBean(DataSource), and the DataSource is only present then), so every
 * component that injects one is itself gated on `accounts.enabled`.
 */
interface UserRepository : CrudRepository<UserRow, Long> {
    fun findByEmail(email: String): UserRow?
}

interface LoginTokenRepository : CrudRepository<LoginTokenRow, Long> {
    fun findByTokenHash(tokenHash: String): LoginTokenRow?
}

interface DeckRepository : CrudRepository<DeckRow, Long> {
    fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<DeckRow>
    fun findByIdAndUserId(id: Long, userId: Long): DeckRow?
    fun deleteByIdAndUserId(id: Long, userId: Long): Int
}

interface MatchResultRepository : CrudRepository<MatchResultRow, Long> {
    @Query("SELECT count(*) FROM match_participants WHERE user_id = :userId")
    fun countGamesForUser(@Param("userId") userId: Long): Long

    @Query("SELECT count(*) FROM match_participants WHERE user_id = :userId AND won = true")
    fun countWinsForUser(@Param("userId") userId: Long): Long
}

interface TournamentRepository : CrudRepository<TournamentRow, Long>
