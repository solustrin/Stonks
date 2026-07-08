package com.trin.stonks.data

import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ImgBBService {
    @Multipart
    @POST("1/upload")
    suspend fun uploadImage(@Query("key") apiKey: String, @Part image: MultipartBody.Part): ImgBBResponse
}

data class ImgBBResponse(val data: ImgBBData)
data class ImgBBData(val url: String)
data class Workspace(val id: String = "", val name: String = "")

class CloudRepository(
    private val db: FirebaseFirestore,
    private val imgbbKey: String
) {
    private val imgBBService = Retrofit.Builder()
        .baseUrl("https://api.imgbb.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ImgBBService::class.java)

    var currentWorkspaceId: String = "default"

    init {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(104857600L).build())
            .build()
        db.firestoreSettings = settings
    }

    private fun workspaceRef() = db.collection("workspaces").document(currentWorkspaceId)
    private fun transactionsRef() = workspaceRef().collection("transactions")
    private fun inventoryRef() = workspaceRef().collection("inventory")
    private fun profilesRef() = workspaceRef().collection("profiles")
    private fun paymentTypesRef() = workspaceRef().collection("paymentTypes")
    private fun metadataRef() = workspaceRef().collection("metadata").document("counter")


    // --- Workspace ---
    fun getWorkspacesFlow(): Flow<List<Workspace>> = callbackFlow {
        val sub = db.collection("workspaces").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { Workspace(it.id, it.getString("name") ?: "Unnamed") } ?: emptyList())
        }
        awaitClose { sub.remove() }
    }

    fun createWorkspace(name: String) = db.collection("workspaces").document().set(mapOf("name" to name))
    fun deleteWorkspace(id: String) = db.collection("workspaces").document(id).delete()
    fun updateWorkspaceName(wId: String, name: String) = db.collection("workspaces").document(wId).update("name", name)

    suspend fun uploadReceipt(imageBytes: ByteArray): String? {
        return try {
            val body = MultipartBody.Part.createFormData("image", "receipt.jpg", imageBytes.toRequestBody("image/jpeg".toMediaType()))
            imgBBService.uploadImage(imgbbKey, body).data.url
        } catch (e: Exception) { null }
    }

    fun update(
        id: String,
        type: String,
        name: String,
        amt: Double,
        qty: Int,
        up: Double,
        cb: String,
        pt: String,
        bid: String,
        note: String,
        receiptUrl: String? = null
    ) {
        val updates = mutableMapOf<String, Any>(
            "type" to type,
            "itemName" to name,
            "amount" to amt,
            "quantity" to qty,
            "unitPrice" to up,
            "createdBy" to cb,
            "paymentType" to pt,
            "batchId" to bid,
            "note" to note
        )
        receiptUrl?.let { updates["receiptUrl"] = it }
        transactionsRef().document(id).update(updates)
    }

    fun updateInventoryItem(id: String, name: String, price: Double, stock: Int) {
        inventoryRef().document(id).update(
            mapOf(
                "name" to name,
                "defaultPrice" to price,
                "currentStock" to stock
            )
        )
    }

    // Just reads the counter to show on the screen (does not increase it)
    suspend fun peekNextInvoiceNumber(): Int {
        return try {
            val snap = metadataRef().get().await()
            val current = snap.getLong("lastInvoiceNumber") ?: 0L
            (current + 1).toInt()
        } catch (e: Exception) {
            1
        }
    }

    // Locks in the number and increases it in the database when saving
    suspend fun getAndIncrementInvoiceNumber(): Int {
        return try {
            db.runTransaction<Int> { transaction ->
                val snap = transaction.get(metadataRef())
                val current = snap.getLong("lastInvoiceNumber") ?: 0L
                val next = current + 1

                transaction.set(metadataRef(), mapOf("lastInvoiceNumber" to next))

                next.toInt()
            }.await()
        } catch (e: Exception) {
            1
        }
    }

    // ============================================================
    // INVOICES (Cloud sync)
    // ============================================================
    suspend fun saveInvoiceToCloud(invoice: CloudInvoice) {
        db.collection("workspaces")
            .document(currentWorkspaceId)
            .collection("invoices")
            .document(invoice.invoiceNumber)
            .set(invoice)
            .await()
    }

    suspend fun deleteCloudInvoice(invoiceNumber: String) {
        db.collection("workspaces")
            .document(currentWorkspaceId)
            .collection("invoices")
            .document(invoiceNumber)
            .delete()
            .await()
    }

    fun getCloudInvoicesFlow(): Flow<List<CloudInvoice>> = callbackFlow {
        val sub = db.collection("workspaces")
            .document(currentWorkspaceId)
            .collection("invoices")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    trySend(snap.documents.mapNotNull { it.toObject(CloudInvoice::class.java) })
                }
            }
        awaitClose { sub.remove() }
    }

    // ============================================================
    // CLIENTS (Cloud sync)
    // ============================================================
    private fun clientsRef() = workspaceRef().collection("clients")

    fun getClientsFlow(): Flow<List<SavedClient>> = callbackFlow {
        val sub = clientsRef().orderBy("name").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull {
                it.toObject(SavedClient::class.java)?.copy(id = it.id)
            } ?: emptyList())
        }
        awaitClose { sub.remove() }
    }

    suspend fun saveClientToCloud(client: SavedClient) {
        val docRef = if (client.id.isNotBlank()) {
            clientsRef().document(client.id)
        } else {
            clientsRef().document()
        }
        val data = mapOf(
            "name" to client.name,
            "contact" to client.contact,
            "address" to client.address
        )
        docRef.set(data).await()
    }

    suspend fun deleteClientFromCloud(clientId: String) {
        clientsRef().document(clientId).delete().await()
    }

    // ============================================================
    // TRANSACTIONS & INVENTORY (unchanged)
    // ============================================================
    fun getAllTransactions(): Flow<List<Transaction>> = callbackFlow {
        val sub = transactionsRef()
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    trySend(snap.documents.map { doc ->
                        Transaction(
                            doc.id, doc.getString("type") ?: "SALE", doc.getString("itemName") ?: "",
                            doc.getDouble("amount") ?: 0.0, (doc.getLong("quantity") ?: 1L).toInt(),
                            doc.getDouble("unitPrice") ?: 0.0, doc.getString("createdBy") ?: "Anonymous",
                            doc.getString("paymentType") ?: "Cash", doc.getString("receiptUrl"),
                            doc.getLong("timestamp") ?: 0L, doc.getString("batchId") ?: "",
                            doc.getString("note") ?: "", doc.getBoolean("affectsStock") ?: false
                        )
                    })
                }
            }
        awaitClose { sub.remove() }
    }

    fun insert(t: Transaction) = transactionsRef().add(t)
    fun delete(id: String) = transactionsRef().document(id).delete()

    fun getInventoryFlow(): Flow<List<InventoryItem>> = callbackFlow {
        val sub = inventoryRef().orderBy("name").addSnapshotListener { snap, _ ->
            if (snap != null) {
                trySend(snap.documents.map { InventoryItem(it.id, it.getString("name") ?: "", it.getDouble("defaultPrice") ?: 0.0, it.getLong("currentStock")?.toInt() ?: 0) })
            }
        }
        awaitClose { sub.remove() }
    }

    suspend fun upsertInventoryItem(name: String, price: Double) {
        val snap = inventoryRef().whereEqualTo("name", name).get().await()
        if (snap.isEmpty) {
            inventoryRef().add(mapOf("name" to name, "defaultPrice" to price, "currentStock" to 0))
        } else {
            val doc = snap.documents.first()
            doc.reference.update("defaultPrice", price)
        }
    }

    suspend fun getInventoryItemIdByName(name: String): String? {
        val snap = inventoryRef().whereEqualTo("name", name).get().await()
        return snap.documents.firstOrNull()?.id
    }

    fun adjustStock(itemId: String, adjustment: Int) = inventoryRef().document(itemId).update("currentStock", FieldValue.increment(adjustment.toLong()))
    fun addInventoryItem(name: String, price: Double, stock: Int) = inventoryRef().add(mapOf("name" to name, "defaultPrice" to price, "currentStock" to stock))
    fun deleteInventoryItem(id: String) = inventoryRef().document(id).delete()

    fun getProfiles(): Flow<List<String>> = callbackFlow {
        val sub = profilesRef().orderBy("name").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.getString("name") } ?: emptyList())
        }
        awaitClose { sub.remove() }
    }
    fun addProfile(name: String) = profilesRef().document(name).set(mapOf("name" to name))
    fun deleteProfile(name: String) = profilesRef().document(name).delete()

    fun getPaymentTypes(): Flow<List<String>> = callbackFlow {
        val sub = paymentTypesRef().orderBy("name").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.mapNotNull { it.getString("name") } ?: emptyList())
        }
        awaitClose { sub.remove() }
    }
    fun addPaymentType(name: String) = paymentTypesRef().document(name).set(mapOf("name" to name))
    fun deletePaymentType(name: String) = paymentTypesRef().document(name).delete()
}