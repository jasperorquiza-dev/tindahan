package com.example.sarisaristore.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.example.sarisaristore.camera.ImageStorageManager
import com.example.sarisaristore.data.local.AppDatabase
import com.example.sarisaristore.data.local.entity.AppSettingsEntity
import com.example.sarisaristore.data.local.entity.CustomerEntity
import com.example.sarisaristore.data.local.entity.GCashTransactionEntity
import com.example.sarisaristore.data.local.entity.ProductActivityLogEntity
import com.example.sarisaristore.data.local.entity.ProductEntity
import com.example.sarisaristore.data.local.entity.SaleEntity
import com.example.sarisaristore.data.local.entity.UtangEntryEntity
import com.example.sarisaristore.data.local.model.GCashTransactionStatus
import com.example.sarisaristore.data.local.model.GCashTransactionType
import com.example.sarisaristore.data.local.model.PaymentMethod
import com.example.sarisaristore.data.local.model.ProductActivityType
import com.example.sarisaristore.data.local.model.ThemeMode
import com.example.sarisaristore.data.local.model.UtangEntryStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.CoroutineContext

internal const val LATEST_BACKUP_FILE_NAME = "sari_store_backup.zip"

private const val TEMP_BACKUP_FILE_PREFIX = "sari_store_backup_pending_"
private const val ROLLBACK_BACKUP_FILE_PREFIX = "sari_store_backup_previous_"
private val LEGACY_BACKUP_FILE_NAME_REGEX = Regex("""^sari_store_backup_\d{8}_\d{6}\.zip$""")

internal fun isManagedBackupFileName(fileName: String): Boolean =
    fileName == LATEST_BACKUP_FILE_NAME ||
        LEGACY_BACKUP_FILE_NAME_REGEX.matches(fileName) ||
        (fileName.startsWith(TEMP_BACKUP_FILE_PREFIX) && fileName.endsWith(".zip")) ||
        (fileName.startsWith(ROLLBACK_BACKUP_FILE_PREFIX) && fileName.endsWith(".zip"))

data class BackupPreview(
    val fileName: String,
    val createdAt: Long,
    val productCount: Int,
    val gcashTransactionCount: Int,
    val utangCustomerCount: Int,
    val utangEntryCount: Int,
    val saleCount: Int,
)

data class BackupResult(
    val fileName: String,
    val createdAt: Long,
)

data class RestoreResult(
    val restoredAt: Long,
    val restoredFileName: String,
    val missingImageCount: Int,
)

class BackupException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class AppBackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val imageStorageManager: ImageStorageManager,
    private val backupPreferences: BackupPreferences,
    private val ioContext: CoroutineContext,
) {
    private val contentResolver: ContentResolver
        get() = context.contentResolver

    fun observePreferences(): StateFlow<BackupPreferencesState> = backupPreferences.observeState()

    fun saveBackupFolder(folderUri: Uri, displayName: String?) {
        backupPreferences.setBackupFolder(folderUri.toString(), displayName)
    }

    suspend fun createBackup(): BackupResult = withContext(ioContext) {
        val folderUri = backupPreferences.getState().folderUri
            ?.let(Uri::parse)
            ?: throw BackupException("Select a backup folder first.")

        val directory = DocumentFile.fromTreeUri(context, folderUri)
            ?.takeIf { it.exists() && it.isDirectory && it.canWrite() }
            ?: throw BackupException("Selected backup folder is no longer available.")

        val settings = database.appSettingsDao().getSettings() ?: AppSettingsEntity.default()
        val products = database.productDao().getAllProducts()
        val sales = database.saleDao().getAllSales()
        val gcashTransactions = database.gcashTransactionDao().getAllTransactions()
        val activityLogs = database.productActivityLogDao().getAllLogs()
        val utangCustomers = database.utangDao().getAllCustomers()
        val utangEntries = database.utangDao().getAllEntries()
        val createdAt = System.currentTimeMillis()

        val imageEntries = LinkedHashMap<String, File>()
        val productsJson = JSONArray().apply {
            products.forEach { product ->
                val imageRef = registerImage(
                    imageEntries = imageEntries,
                    originalPath = product.imagePath,
                    directoryName = "products",
                )
                put(
                    JSONObject()
                        .put("id", product.id)
                        .put("name", product.name)
                        .put("imageRef", imageRef)
                        .put("priceCentavos", product.priceCentavos)
                        .put("stockQuantity", product.stockQuantity)
                        .put("category", product.category)
                        .put("createdAt", product.createdAt)
                        .put("updatedAt", product.updatedAt),
                )
            }
        }
        val salesJson = JSONArray().apply {
            sales.forEach { sale ->
                put(
                    JSONObject()
                        .put("id", sale.id)
                        .put("productId", sale.productId)
                        .put("productNameSnapshot", sale.productNameSnapshot)
                        .put("quantity", sale.quantity)
                        .put("unitPriceCentavos", sale.unitPriceCentavos)
                        .put("totalAmountCentavos", sale.totalAmountCentavos)
                        .put("paymentMethod", sale.paymentMethod.name)
                        .put("note", sale.note)
                        .put("createdAt", sale.createdAt),
                )
            }
        }
        val gcashJson = JSONArray().apply {
            gcashTransactions.forEach { transaction ->
                val receiptImageRef = registerImage(
                    imageEntries = imageEntries,
                    originalPath = transaction.receiptImagePath,
                    directoryName = "receipts",
                )
                val signatureImageRef = registerImage(
                    imageEntries = imageEntries,
                    originalPath = transaction.signatureImagePath,
                    directoryName = "signatures",
                )
                put(
                    JSONObject()
                        .put("id", transaction.id)
                        .put("type", transaction.type.name)
                        .put("amountCentavos", transaction.amountCentavos)
                        .put("serviceFeeCentavos", transaction.serviceFeeCentavos)
                        .put("customerName", transaction.customerName)
                        .put("receiptImageRef", receiptImageRef)
                        .put("signatureImageRef", signatureImageRef)
                        .put("note", transaction.note)
                        .put("status", transaction.status.name)
                        .put("createdAt", transaction.createdAt),
                )
            }
        }
        val activityLogsJson = JSONArray().apply {
            activityLogs.forEach { log ->
                put(
                    JSONObject()
                        .put("id", log.id)
                        .put("productId", log.productId)
                        .put("productNameSnapshot", log.productNameSnapshot)
                        .put("activityType", log.activityType.name)
                        .put("quantityDelta", log.quantityDelta)
                        .put("note", log.note)
                        .put("createdAt", log.createdAt),
                )
            }
        }
        val utangCustomersJson = JSONArray().apply {
            utangCustomers.forEach { customer ->
                put(
                    JSONObject()
                        .put("id", customer.id)
                        .put("name", customer.name)
                        .put("createdAt", customer.createdAt)
                        .put("updatedAt", customer.updatedAt),
                )
            }
        }
        val utangEntriesJson = JSONArray().apply {
            utangEntries.forEach { entry ->
                put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("customerId", entry.customerId)
                        .put("noteText", entry.noteText)
                        .put("amountCentavos", entry.amountCentavos)
                        .put("status", entry.status.name)
                        .put("createdAt", entry.createdAt)
                        .put("updatedAt", entry.updatedAt)
                        .put("paidAt", entry.paidAt),
                )
            }
        }

        val backupJson = JSONObject()
            .put("version", BACKUP_VERSION)
            .put("createdAt", createdAt)
            .put("settings", JSONObject()
                .put("id", settings.id)
                .put("storeTitle", settings.storeTitle)
                .put("passcodeEnabled", settings.passcodeEnabled)
                .put("passcodeSalt", settings.passcodeSalt)
                .put("passcodeHash", settings.passcodeHash)
                .put("lowStockThreshold", settings.lowStockThreshold)
                .put("themeMode", settings.themeMode.name))
            .put("products", productsJson)
            .put("sales", salesJson)
            .put("gcashTransactions", gcashJson)
            .put("productActivityLogs", activityLogsJson)
            .put("utangCustomers", utangCustomersJson)
            .put("utangEntries", utangEntriesJson)

        val tempBackupFile = createTemporaryBackupFile(
            directory = directory,
            backupJson = backupJson,
            imageEntries = imageEntries,
        )
        val finalBackupFile = promoteBackupFile(
            directory = directory,
            tempBackupFile = tempBackupFile,
        )
        val fileName = finalBackupFile.name ?: LATEST_BACKUP_FILE_NAME
        backupPreferences.updateLastBackup(fileName = fileName, createdAt = createdAt)
        BackupResult(fileName = fileName, createdAt = createdAt)
    }

    suspend fun validateBackup(sourceUri: Uri): BackupPreview = withContext(ioContext) {
        val payload = readBackupPayload(sourceUri)
        val fileName = DocumentFile.fromSingleUri(context, sourceUri)?.name ?: DEFAULT_RESTORE_FILE_NAME
        BackupPreview(
            fileName = fileName,
            createdAt = payload.createdAt,
            productCount = payload.products.size,
            gcashTransactionCount = payload.gcashTransactions.size,
            utangCustomerCount = payload.utangCustomers.size,
            utangEntryCount = payload.utangEntries.size,
            saleCount = payload.sales.size,
        )
    }

    suspend fun restoreBackup(sourceUri: Uri): RestoreResult = withContext(ioContext) {
        val payload = readBackupPayload(sourceUri)
        val extractedImages = extractImagesToTemp(sourceUri, referencedImageRefs(payload))
        val finalImagePaths = extractedImages.mapValues { (archivePath, _) ->
            imageStorageManager.resolveRestoredImageFile(archivePath).absolutePath
        }

        database.withTransaction {
            database.clearAllTables()
            database.appSettingsDao().upsertSettings(payload.settings.toEntity())
            payload.products.forEach { product ->
                database.productDao().insertProduct(product.toEntity(finalImagePaths[product.imageRef]))
            }
            payload.sales.forEach { sale ->
                database.saleDao().insertSale(sale.toEntity())
            }
            payload.gcashTransactions.forEach { transaction ->
                database.gcashTransactionDao().insertTransaction(
                    transaction.toEntity(
                        receiptImagePath = finalImagePaths[transaction.receiptImageRef].orEmpty(),
                        signatureImagePath = finalImagePaths[transaction.signatureImageRef],
                    ),
                )
            }
            payload.productActivityLogs.forEach { log ->
                database.productActivityLogDao().insertLog(log.toEntity())
            }
            payload.utangCustomers.forEach { customer ->
                database.utangDao().insertCustomer(customer.toEntity())
            }
            payload.utangEntries.forEach { entry ->
                database.utangDao().insertEntry(entry.toEntity())
            }
        }

        imageStorageManager.clearAllImages()
        extractedImages.forEach { (archivePath, tempFile) ->
            val destination = File(finalImagePaths.getValue(archivePath))
            destination.parentFile?.mkdirs()
            tempFile.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }

        extractedImages.values.forEach { tempFile ->
            runCatching { tempFile.delete() }
        }
        extractedImages.values.firstOrNull()?.parentFile?.let { tempDirectory ->
            runCatching { tempDirectory.deleteRecursively() }
        }

        RestoreResult(
            restoredAt = System.currentTimeMillis(),
            restoredFileName = DocumentFile.fromSingleUri(context, sourceUri)?.name ?: DEFAULT_RESTORE_FILE_NAME,
            missingImageCount = referencedImageRefs(payload).size - extractedImages.size,
        )
    }

    private fun registerImage(
        imageEntries: LinkedHashMap<String, File>,
        originalPath: String?,
        directoryName: String,
    ): String? {
        if (originalPath.isNullOrBlank()) {
            return null
        }

        val sourceFile = File(originalPath)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return null
        }

        imageEntries.entries.firstOrNull { it.value.absolutePath == sourceFile.absolutePath }?.let {
            return it.key
        }

        val baseName = sourceFile.name.takeIf { it.isNotBlank() } ?: "image_${imageEntries.size + 1}.jpg"
        var archivePath = "images/$directoryName/$baseName"
        var duplicateIndex = 1
        while (imageEntries.containsKey(archivePath)) {
            archivePath = "images/$directoryName/${sourceFile.nameWithoutExtension}_$duplicateIndex.${sourceFile.extension.ifBlank { "jpg" }}"
            duplicateIndex += 1
        }
        imageEntries[archivePath] = sourceFile
        return archivePath
    }

    private fun readBackupPayload(sourceUri: Uri): BackupPayload {
        try {
            contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                ZipInputStream(inputStream.buffered()).use { zipInputStream ->
                    generateSequence { zipInputStream.nextEntry }
                        .firstOrNull { it.name == BACKUP_JSON_ENTRY }
                        ?.let {
                            val jsonText = zipInputStream.readBytes().toString(Charsets.UTF_8)
                            return parseBackupPayload(JSONObject(jsonText))
                        }
                }
            }
        } catch (exception: JSONException) {
            throw BackupException("Backup file is corrupted or unreadable.", exception)
        } catch (exception: IOException) {
            throw BackupException("Could not open the backup file.", exception)
        }

        throw BackupException("Selected file is not a valid app backup.")
    }

    private fun parseBackupPayload(root: JSONObject): BackupPayload {
        val version = root.optInt("version", -1)
        if (version != BACKUP_VERSION) {
            throw BackupException("This backup version is not supported.")
        }

        val settingsObject = root.optJSONObject("settings")
            ?: throw BackupException("Backup settings data is missing.")

        return BackupPayload(
            createdAt = root.optLong("createdAt", 0L),
            settings = SettingsBackupRecord(
                id = settingsObject.optInt("id", AppSettingsEntity.SINGLETON_ID),
                storeTitle = settingsObject.optString("storeTitle", "My Store"),
                passcodeEnabled = settingsObject.optBoolean("passcodeEnabled", false),
                passcodeSalt = settingsObject.optStringOrNull("passcodeSalt"),
                passcodeHash = settingsObject.optStringOrNull("passcodeHash"),
                lowStockThreshold = settingsObject.optInt("lowStockThreshold", 5),
                themeMode = settingsObject.optEnum("themeMode", ThemeMode.SYSTEM),
            ),
            products = root.optJSONArray("products").toList {
                ProductBackupRecord(
                    id = optLong("id"),
                    name = optString("name"),
                    imageRef = optStringOrNull("imageRef"),
                    priceCentavos = optLong("priceCentavos"),
                    stockQuantity = optInt("stockQuantity"),
                    category = optStringOrNull("category"),
                    createdAt = optLong("createdAt"),
                    updatedAt = optLong("updatedAt"),
                )
            },
            sales = root.optJSONArray("sales").toList {
                SaleBackupRecord(
                    id = optLong("id"),
                    productId = optLongOrNull("productId"),
                    productNameSnapshot = optString("productNameSnapshot"),
                    quantity = optInt("quantity"),
                    unitPriceCentavos = optLong("unitPriceCentavos"),
                    totalAmountCentavos = optLong("totalAmountCentavos"),
                    paymentMethod = optEnum("paymentMethod", PaymentMethod.CASH),
                    note = optStringOrNull("note"),
                    createdAt = optLong("createdAt"),
                )
            },
            gcashTransactions = root.optJSONArray("gcashTransactions").toList {
                GCashTransactionBackupRecord(
                    id = optLong("id"),
                    type = optEnum("type", GCashTransactionType.CASH_IN),
                    amountCentavos = optLong("amountCentavos"),
                    serviceFeeCentavos = optLong("serviceFeeCentavos"),
                    customerName = optStringOrNull("customerName"),
                    receiptImageRef = optStringOrNull("receiptImageRef"),
                    signatureImageRef = optStringOrNull("signatureImageRef"),
                    note = optStringOrNull("note"),
                    status = optEnum("status", GCashTransactionStatus.COMPLETED),
                    createdAt = optLong("createdAt"),
                )
            },
            productActivityLogs = root.optJSONArray("productActivityLogs").toList {
                ProductActivityBackupRecord(
                    id = optLong("id"),
                    productId = optLongOrNull("productId"),
                    productNameSnapshot = optString("productNameSnapshot"),
                    activityType = optEnum("activityType", ProductActivityType.CREATED),
                    quantityDelta = optIntOrNull("quantityDelta"),
                    note = optStringOrNull("note"),
                    createdAt = optLong("createdAt"),
                )
            },
            utangCustomers = root.optJSONArray("utangCustomers").toList {
                CustomerBackupRecord(
                    id = optLong("id"),
                    name = optString("name"),
                    createdAt = optLong("createdAt"),
                    updatedAt = optLong("updatedAt"),
                )
            },
            utangEntries = root.optJSONArray("utangEntries").toList {
                UtangEntryBackupRecord(
                    id = optLong("id"),
                    customerId = optLong("customerId"),
                    noteText = optString("noteText"),
                    amountCentavos = optLongOrNull("amountCentavos"),
                    status = optEnum("status", UtangEntryStatus.UNPAID),
                    createdAt = optLong("createdAt"),
                    updatedAt = optLong("updatedAt"),
                    paidAt = optLongOrNull("paidAt"),
                )
            },
        )
    }

    private fun referencedImageRefs(payload: BackupPayload): Set<String> = buildSet {
        payload.products.mapNotNullTo(this) { it.imageRef }
        payload.gcashTransactions.mapNotNullTo(this) { it.receiptImageRef }
        payload.gcashTransactions.mapNotNullTo(this) { it.signatureImageRef }
    }

    private fun extractImagesToTemp(
        sourceUri: Uri,
        referencedImageRefs: Set<String>,
    ): Map<String, File> {
        if (referencedImageRefs.isEmpty()) {
            return emptyMap()
        }

        val tempDirectory = File(context.cacheDir, "restore_images_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
        val extractedFiles = mutableMapOf<String, File>()

        try {
            contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                ZipInputStream(inputStream.buffered()).use { zipInputStream ->
                    while (true) {
                        val entry = zipInputStream.nextEntry ?: break
                        if (entry.isDirectory || entry.name !in referencedImageRefs) {
                            continue
                        }
                        val safeName = entry.name
                            .replace('/', '_')
                            .replace('\\', '_')
                        val tempFile = File(tempDirectory, safeName)
                        tempFile.outputStream().use { output -> zipInputStream.copyTo(output) }
                        extractedFiles[entry.name] = tempFile
                    }
                }
            } ?: throw BackupException("Could not open the backup ZIP.")
            return extractedFiles
        } catch (exception: IOException) {
            tempDirectory.deleteRecursively()
            throw BackupException("Could not extract images from the backup.", exception)
        }
    }

    private fun createTemporaryBackupFile(
        directory: DocumentFile,
        backupJson: JSONObject,
        imageEntries: LinkedHashMap<String, File>,
    ): DocumentFile {
        val tempFileName = buildTemporaryBackupFileName()
        val tempBackupFile = directory.createFile("application/zip", tempFileName)
            ?: throw BackupException("Could not create a temporary backup file in the selected folder.")

        try {
            writeBackupZip(
                targetFile = tempBackupFile,
                backupJson = backupJson,
                imageEntries = imageEntries,
            )
        } catch (exception: Exception) {
            runCatching { tempBackupFile.delete() }
            throw exception
        }

        return tempBackupFile
    }

    private fun writeBackupZip(
        targetFile: DocumentFile,
        backupJson: JSONObject,
        imageEntries: LinkedHashMap<String, File>,
    ) {
        contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
            ZipOutputStream(outputStream.buffered()).use { zipOutputStream ->
                zipOutputStream.putNextEntry(ZipEntry(BACKUP_JSON_ENTRY))
                zipOutputStream.write(backupJson.toString().toByteArray(Charsets.UTF_8))
                zipOutputStream.closeEntry()

                imageEntries.forEach { (archivePath, sourceFile) ->
                    zipOutputStream.putNextEntry(ZipEntry(archivePath))
                    sourceFile.inputStream().use { input -> input.copyTo(zipOutputStream) }
                    zipOutputStream.closeEntry()
                }
            }
        } ?: throw BackupException("Could not write the backup ZIP.")
    }

    private fun promoteBackupFile(
        directory: DocumentFile,
        tempBackupFile: DocumentFile,
    ): DocumentFile {
        val existingBackupFile = directory.findFile(LATEST_BACKUP_FILE_NAME)
            ?.takeIf { it.isFile }

        if (existingBackupFile == null) {
            return renameManagedFile(
                directory = directory,
                sourceFile = tempBackupFile,
                targetFileName = LATEST_BACKUP_FILE_NAME,
                errorMessage = "Could not finalize the backup file.",
            ).also { finalBackupFile ->
                cleanupManagedBackupFiles(directory, keepUri = finalBackupFile.uri)
            }
        }

        val parkedBackupFile = renameManagedFile(
            directory = directory,
            sourceFile = existingBackupFile,
            targetFileName = buildRollbackBackupFileName(),
            errorMessage = "Could not prepare the existing backup for replacement.",
        )

        return try {
            renameManagedFile(
                directory = directory,
                sourceFile = tempBackupFile,
                targetFileName = LATEST_BACKUP_FILE_NAME,
                errorMessage = "Could not replace the existing backup file.",
            ).also { finalBackupFile ->
                cleanupManagedBackupFiles(directory, keepUri = finalBackupFile.uri)
            }
        } catch (exception: Exception) {
            val restoredPreviousBackup = runCatching {
                renameManagedFile(
                    directory = directory,
                    sourceFile = parkedBackupFile,
                    targetFileName = LATEST_BACKUP_FILE_NAME,
                    errorMessage = "Could not restore the previous backup file.",
                )
            }.isSuccess
            runCatching { tempBackupFile.delete() }

            if (restoredPreviousBackup) {
                throw BackupException(
                    "Could not replace the existing backup. The previous backup was restored.",
                    exception,
                )
            }

            throw BackupException(
                "Could not replace the existing backup safely. The previous backup was kept in the selected folder.",
                exception,
            )
        }
    }

    private fun renameManagedFile(
        directory: DocumentFile,
        sourceFile: DocumentFile,
        targetFileName: String,
        errorMessage: String,
    ): DocumentFile {
        if (!sourceFile.renameTo(targetFileName)) {
            throw BackupException(errorMessage)
        }

        return directory.findFile(targetFileName)
            ?.takeIf { it.isFile }
            ?: sourceFile.takeIf { it.isFile && it.name == targetFileName }
            ?: throw BackupException(errorMessage)
    }

    private fun cleanupManagedBackupFiles(
        directory: DocumentFile,
        keepUri: Uri,
    ) {
        directory.listFiles()
            .filter { file ->
                file.isFile &&
                    file.uri != keepUri &&
                    file.name?.let(::isManagedBackupFileName) == true
            }
            .forEach { staleFile ->
                runCatching { staleFile.delete() }
            }
    }

    private fun buildTemporaryBackupFileName(): String =
        "$TEMP_BACKUP_FILE_PREFIX${System.currentTimeMillis()}.zip"

    private fun buildRollbackBackupFileName(): String =
        "$ROLLBACK_BACKUP_FILE_PREFIX${System.currentTimeMillis()}.zip"

    private fun JSONObject.optStringOrNull(key: String): String? =
        takeIf { has(key) && !isNull(key) }
            ?.optString(key)
            ?.takeIf { it.isNotBlank() }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        takeIf { has(key) && !isNull(key) }?.optLong(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        takeIf { has(key) && !isNull(key) }?.optInt(key)

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, defaultValue: T): T {
        val value = optStringOrNull(key) ?: return defaultValue
        return enumValues<T>().firstOrNull { it.name == value } ?: defaultValue
    }

    private inline fun <T> JSONArray?.toList(transform: JSONObject.() -> T): List<T> {
        if (this == null) {
            return emptyList()
        }
        return buildList(length()) {
            for (index in 0 until length()) {
                add(getJSONObject(index).transform())
            }
        }
    }

    private data class BackupPayload(
        val createdAt: Long,
        val settings: SettingsBackupRecord,
        val products: List<ProductBackupRecord>,
        val sales: List<SaleBackupRecord>,
        val gcashTransactions: List<GCashTransactionBackupRecord>,
        val productActivityLogs: List<ProductActivityBackupRecord>,
        val utangCustomers: List<CustomerBackupRecord>,
        val utangEntries: List<UtangEntryBackupRecord>,
    )

    private data class SettingsBackupRecord(
        val id: Int,
        val storeTitle: String,
        val passcodeEnabled: Boolean,
        val passcodeSalt: String?,
        val passcodeHash: String?,
        val lowStockThreshold: Int,
        val themeMode: ThemeMode,
    ) {
        fun toEntity(): AppSettingsEntity = AppSettingsEntity(
            id = id,
            storeTitle = storeTitle,
            passcodeEnabled = passcodeEnabled,
            passcodeSalt = passcodeSalt,
            passcodeHash = passcodeHash,
            lowStockThreshold = lowStockThreshold,
            themeMode = themeMode,
        )
    }

    private data class ProductBackupRecord(
        val id: Long,
        val name: String,
        val imageRef: String?,
        val priceCentavos: Long,
        val stockQuantity: Int,
        val category: String?,
        val createdAt: Long,
        val updatedAt: Long,
    ) {
        fun toEntity(imagePath: String?): ProductEntity = ProductEntity(
            id = id,
            name = name,
            imagePath = imagePath,
            priceCentavos = priceCentavos,
            stockQuantity = stockQuantity,
            category = category,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private data class SaleBackupRecord(
        val id: Long,
        val productId: Long?,
        val productNameSnapshot: String,
        val quantity: Int,
        val unitPriceCentavos: Long,
        val totalAmountCentavos: Long,
        val paymentMethod: PaymentMethod,
        val note: String?,
        val createdAt: Long,
    ) {
        fun toEntity(): SaleEntity = SaleEntity(
            id = id,
            productId = productId,
            productNameSnapshot = productNameSnapshot,
            quantity = quantity,
            unitPriceCentavos = unitPriceCentavos,
            totalAmountCentavos = totalAmountCentavos,
            paymentMethod = paymentMethod,
            note = note,
            createdAt = createdAt,
        )
    }

    private data class GCashTransactionBackupRecord(
        val id: Long,
        val type: GCashTransactionType,
        val amountCentavos: Long,
        val serviceFeeCentavos: Long,
        val customerName: String?,
        val receiptImageRef: String?,
        val signatureImageRef: String?,
        val note: String?,
        val status: GCashTransactionStatus,
        val createdAt: Long,
    ) {
        fun toEntity(
            receiptImagePath: String,
            signatureImagePath: String?,
        ): GCashTransactionEntity = GCashTransactionEntity(
            id = id,
            type = type,
            amountCentavos = amountCentavos,
            serviceFeeCentavos = serviceFeeCentavos,
            customerName = customerName,
            receiptImagePath = receiptImagePath,
            signatureImagePath = signatureImagePath,
            note = note,
            status = status,
            createdAt = createdAt,
        )
    }

    private data class ProductActivityBackupRecord(
        val id: Long,
        val productId: Long?,
        val productNameSnapshot: String,
        val activityType: ProductActivityType,
        val quantityDelta: Int?,
        val note: String?,
        val createdAt: Long,
    ) {
        fun toEntity(): ProductActivityLogEntity = ProductActivityLogEntity(
            id = id,
            productId = productId,
            productNameSnapshot = productNameSnapshot,
            activityType = activityType,
            quantityDelta = quantityDelta,
            note = note,
            createdAt = createdAt,
        )
    }

    private data class CustomerBackupRecord(
        val id: Long,
        val name: String,
        val createdAt: Long,
        val updatedAt: Long,
    ) {
        fun toEntity(): CustomerEntity = CustomerEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private data class UtangEntryBackupRecord(
        val id: Long,
        val customerId: Long,
        val noteText: String,
        val amountCentavos: Long?,
        val status: UtangEntryStatus,
        val createdAt: Long,
        val updatedAt: Long,
        val paidAt: Long?,
    ) {
        fun toEntity(): UtangEntryEntity = UtangEntryEntity(
            id = id,
            customerId = customerId,
            noteText = noteText,
            amountCentavos = amountCentavos,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            paidAt = paidAt,
        )
    }

    private companion object {
        const val BACKUP_VERSION = 1
        const val BACKUP_JSON_ENTRY = "backup.json"
        const val DEFAULT_RESTORE_FILE_NAME = LATEST_BACKUP_FILE_NAME
    }
}
