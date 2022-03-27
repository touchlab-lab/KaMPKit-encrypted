package co.touchlab.kampkit

import co.touchlab.kampkit.db.KaMPKitDb
import co.touchlab.kermit.Logger
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseFileContext
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.databaseFileExists
import co.touchlab.sqliter.getVersion
import co.touchlab.sqliter.setVersion
import com.russhwolf.settings.AppleSettings
import com.russhwolf.settings.Settings
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver
import com.squareup.sqldelight.drivers.native.wrapConnection
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

fun initKoinIos(
    userDefaults: NSUserDefaults,
    appInfo: AppInfo,
    doOnStartup: () -> Unit
): KoinApplication = initKoin(
    module {
        single<Settings> { AppleSettings(userDefaults) }
        single { appInfo }
        single { doOnStartup }
    }
)

actual val platformModule = module {
    // createClearDb()
    createEncryptedDb()

    single { Darwin.create() }
}

private fun Module.createClearDb() {
    single<SqlDriver> { NativeSqliteDriver(KaMPKitDb.Schema, "KampkitDb") }
}

private fun Module.createEncryptedDb() {
    val unsafeEncryptionKey = "abacab"
    val clearDbName = "KampkitDb"
    val encryptedDbName = "KampkitDb2"

    if (DatabaseFileContext.databaseFileExists(clearDbName)) {
        val fromManager = createDatabaseManager(DatabaseConfiguration(
            name = clearDbName,
            version = NO_VERSION_CHECK,
            create = {}
        ))

        val toManager = createDatabaseManager(
            DatabaseConfiguration(
                name = encryptedDbName,
                version = NO_VERSION_CHECK,
                create = {},
                encryptionConfig = DatabaseConfiguration.Encryption(unsafeEncryptionKey)
            )
        )

        //Force-create the target db
        toManager.createMultiThreadedConnection().close()

        val fromConn = fromManager.createMultiThreadedConnection()

        val encryptedDbPath = DatabaseFileContext.databasePath(
            encryptedDbName,
            null
        )

        fromConn.rawExecSql(
            "ATTACH DATABASE '$encryptedDbPath' AS encrypted KEY '${unsafeEncryptionKey}'"
        )
        fromConn.rawExecSql("select sqlcipher_export('encrypted')")
        fromConn.rawExecSql("DETACH DATABASE encrypted")

        //Don't forget the version!!!
        toManager.createMultiThreadedConnection().apply {
            setVersion(fromConn.getVersion())
            close()
        }

        DatabaseFileContext.deleteDatabase(clearDbName, null)
    }

    val dbConfig = DatabaseConfiguration(
        name = encryptedDbName,
        version = KaMPKitDb.Schema.version,
        create = { connection ->
            wrapConnection(connection) { KaMPKitDb.Schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { KaMPKitDb.Schema.migrate(it, oldVersion, newVersion) }
        },
        encryptionConfig = DatabaseConfiguration.Encryption(unsafeEncryptionKey)
    )
    single<SqlDriver> { NativeSqliteDriver(dbConfig) }
}

// Access from Swift to create a logger
@Suppress("unused")
fun Koin.loggerWithTag(tag: String) =
    get<Logger>(qualifier = null) { parametersOf(tag) }
