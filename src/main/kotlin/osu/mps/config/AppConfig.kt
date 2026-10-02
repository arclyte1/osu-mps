package osu.mps.config

object AppConfig {
    fun jdbcUrl(): String =
        env("DATABASE_JDBC_URL") ?: "jdbc:postgresql://localhost:5001/mps"

    fun dbUser(): String =
        env("DATABASE_USER", "POSTGRES_USER") ?: "postgres"

    fun dbPassword(): String =
        env("DATABASE_PASSWORD", "POSTGRES_PASSWORD").orEmpty()

    fun osuClientId(): String =
        env("OSU_CLIENT_ID") ?: missing("OSU_CLIENT_ID")

    fun osuClientSecret(): String =
        env("OSU_CLIENT_SECRET") ?: missing("OSU_CLIENT_SECRET")

    fun startMatchId(): Int =
        envInt("START_MATCH_ID") ?: missing("START_MATCH_ID")

    fun endMatchId(): Int? = envInt("END_MATCH_ID")

    fun osuApiRateLimit(): Int = envInt("OSU_API_RATE_LIMIT") ?: 60

    private fun env(vararg keys: String): String? {
        for (key in keys) {
            System.getenv(key)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun envInt(vararg keys: String): Int? {
        return env(*keys)?.toIntOrNull()
    }

    private fun missing(name: String): Nothing =
        error("Environment variable $name is not set (IDE run configuration or .env)")
}
