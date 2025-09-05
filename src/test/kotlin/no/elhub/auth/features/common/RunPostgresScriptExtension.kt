package no.elhub.auth.features.common

import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import org.apache.ibatis.io.Resources
import org.apache.ibatis.jdbc.ScriptRunner
import java.sql.Connection
import java.sql.DriverManager
import java.util.Collections

/**
 * @param scriptResourcePath
 *   The **classpath** location of an SQL file, relative to `src/test/resources`.
 */
class RunPostgresScriptExtension(
    private val scriptResourcePath: String,
) : BeforeSpecListener {
    companion object {
        private const val JDBC_URL = "jdbc:postgresql://localhost:5432/auth"
        private const val JDBC_USER = "app"
        private const val JDBC_PASSWORD = "app"

        private val executedScripts: MutableSet<String> =
            Collections.synchronizedSet(mutableSetOf())

        private fun getConnection(): Connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)
    }

    override suspend fun beforeSpec(spec: Spec) {
        val firstTime = executedScripts.add(scriptResourcePath)
        if (firstTime) {
            getConnection().use { connection ->
                val runner = ScriptRunner(connection)
                val reader = Resources.getResourceAsReader(scriptResourcePath)

                runner.runScript(reader)
                reader.close()
                println("Executed script: $scriptResourcePath")
            }
        }
    }
}
