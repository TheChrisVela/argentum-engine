package com.wingedsheep.gameserver.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

/**
 * Proves the V1 migration is valid PostgreSQL and the schema supports the account/deck/stats
 * round-trip — including the win-count query backing [MatchResultRepository.countWinsForUser].
 *
 * Self-skips when Docker is unavailable, so CI/dev boxes without Docker still pass.
 */
class FlywayMigrationTest : FunSpec({

    val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)

    test("V1 migration applies and supports account/deck/stats round-trips").config(enabled = dockerAvailable) {
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine"))
        postgres.start()
        try {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_name IN ('users','login_tokens','decks','match_results','match_participants')
                        """.trimIndent()
                    ).use { rs ->
                        rs.next()
                        rs.getInt(1) shouldBe 5
                    }

                    st.execute("INSERT INTO users(id, email, display_name) VALUES (1, 'a@test.com', 'a')")
                    st.execute("INSERT INTO decks(user_id, name, format, data) VALUES (1, 'My Deck', 'STANDARD', '{}')")
                    st.execute("INSERT INTO match_results(id, game_id) VALUES (10, 'g1')")
                    st.execute("INSERT INTO match_participants(match_id, user_id, player_name, won) VALUES (10, 1, 'a', true)")
                    st.execute("INSERT INTO match_participants(match_id, user_id, player_name, won) VALUES (10, NULL, 'guest', false)")

                    st.executeQuery("SELECT count(*) FROM match_participants WHERE user_id = 1 AND won = true").use { rs ->
                        rs.next()
                        rs.getInt(1) shouldBe 1
                    }
                    st.executeQuery("SELECT count(*) FROM decks WHERE user_id = 1").use { rs ->
                        rs.next()
                        rs.getInt(1) shouldBe 1
                    }
                }
            }
        } finally {
            postgres.stop()
        }
    }

    test("V2 migration adds the stats schema and supports its aggregate queries").config(enabled = dockerAvailable) {
        val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine"))
        postgres.start()
        try {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
                conn.createStatement().use { st ->
                    // New tables exist.
                    st.executeQuery(
                        """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_name IN ('match_participant_cards','tournaments','tournament_participants')
                        """.trimIndent()
                    ).use { rs -> rs.next(); rs.getInt(1) shouldBe 3 }

                    // A signed-in winner (WU) vs a guest, with deck cards.
                    st.execute("INSERT INTO users(id, email, display_name) VALUES (1, 'a@test.com', 'Alice')")
                    st.execute("INSERT INTO match_results(id, game_id, game_mode, frame_count, turn_count) VALUES (10, 'g1', 'CASUAL', 22, 7)")
                    st.execute("INSERT INTO match_participants(id, match_id, user_id, player_name, won, colors, set_codes, is_ai, client_ip) VALUES (100, 10, 1, 'Alice', true, 'WU', 'DSK,BLB', false, '8.8.8.8')")
                    st.execute("INSERT INTO match_participants(id, match_id, user_id, player_name, won, colors, set_codes, is_ai) VALUES (101, 10, NULL, 'Guest', false, 'R', 'DSK', false)")
                    st.execute("INSERT INTO match_participant_cards(participant_id, card_name, copies) VALUES (100, 'Island', 9), (100, 'Plains', 8), (101, 'Mountain', 17)")

                    // set_codes splitting (unnest + string_to_array).
                    st.executeQuery(
                        """
                        SELECT count(*) FROM match_participants p
                        CROSS JOIN LATERAL unnest(string_to_array(p.set_codes, ',')) AS s
                        WHERE p.user_id = 1
                        """.trimIndent()
                    ).use { rs -> rs.next(); rs.getInt(1) shouldBe 2 }

                    // Card win rate (FILTER): Island appears in one deck, which won.
                    st.executeQuery(
                        """
                        SELECT count(*) FILTER (WHERE pp.won) FROM match_participant_cards c
                        JOIN match_participants pp ON pp.id = c.participant_id
                        WHERE c.card_name = 'Island'
                        """.trimIndent()
                    ).use { rs -> rs.next(); rs.getInt(1) shouldBe 1 }

                    // Games-per-day bucketing.
                    st.executeQuery(
                        "SELECT count(*) FROM match_results WHERE ended_at >= now() - (30 * interval '1 day')"
                    ).use { rs -> rs.next(); rs.getInt(1) shouldBe 1 }

                    // Tournament round-trip with a placement.
                    st.execute("INSERT INTO tournaments(id, lobby_id, name, format, game_mode, player_count, winner_name) VALUES (200, 'lob1', 'Sealed', 'SEALED', 'TOURNAMENT', 4, 'Alice')")
                    st.execute("INSERT INTO tournament_participants(tournament_id, user_id, player_name, is_ai, placement, wins, losses, draws) VALUES (200, 1, 'Alice', false, 1, 3, 0, 0)")
                    st.executeQuery("SELECT placement FROM tournament_participants WHERE user_id = 1").use { rs ->
                        rs.next(); rs.getInt(1) shouldBe 1
                    }

                    // Deleting a match cascades to its cards.
                    st.execute("DELETE FROM match_results WHERE id = 10")
                    st.executeQuery("SELECT count(*) FROM match_participant_cards").use { rs ->
                        rs.next(); rs.getInt(1) shouldBe 0
                    }
                }
            }
        } finally {
            postgres.stop()
        }
    }
})
