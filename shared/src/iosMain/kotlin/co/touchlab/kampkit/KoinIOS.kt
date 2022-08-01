package co.touchlab.kampkit

import co.touchlab.kampkit.db.KaMPKitDb
import co.touchlab.kermit.Logger
import co.touchlab.sqliter.DatabaseConfiguration
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

private fun Module.createEncryptedDb() {
    val unsafeEncryptionKey = "abacab"
    val encryptedDbName = "KampkitDb2"

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
