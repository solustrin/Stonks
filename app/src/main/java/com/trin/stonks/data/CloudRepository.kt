package com.trin.stonks.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType

// ImgBB API interface (no album parameter)
interface ImgBBService {
    @Multipart
    @POST("1/upload")
    suspend fun uploadImage(
        @Query("key") apiKey: String,
        @Part image: MultipartBody.Part
    ): ImgBBResponse
}

data class ImgBBResponse(val data: ImgBBData)
data class ImgBBData(val url: String)

data class Workspace(
    val id: String = "",
    val name: String = ""
)

class CloudRepository {
    private val db = FirebaseFirestore.getInstance()

    // Your ImgBB API key
    private val MY_IMGBB_KEY = "e3b006ac252210fe03d3829a30ebeefb"

    private val imgBBService = Retrofit.Builder()
        .baseUrl("https://api.imgbb.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ImgBBService::class.java)

    init {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(104857600L)
                    .build()
            )
            .build()
        db.firestoreSettings = settings
    }

    var currentWorkspaceId: String = "default"

    private fun workspaceRef() = db.collection("workspaces").document(currentWorkspaceId)
    private fun transactionsRef() = workspaceRef().collection("transactions")
    private fun profilesRef() = workspaceRef().collection("profiles")
    private fun paymentTypesRef() = workspaceRef().collection("paymentTypes")

    // --- Workspace management ---
    fun getWorkspacesFlow(): Flow<List<Workspace>> = callbackFlow {
        val subscription = db.collection("workspaces")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    Workspace(id = doc.id, name = doc.getString("name") ?: "Unnamed")
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    fun createWorkspace(name: String) {
        val doc = db.collection("workspaces").document()
        doc.set(mapOf("name" to name))
    }

    fun deleteWorkspace(id: String) {
        db.collection("workspaces").document(id).delete()
    }

    fun updateWorkspaceName(workspaceId: String, newName: String) {
        db.collection("workspaces").document(workspaceId).update("name", newName)
    }

    // --- Upload receipt to ImgBB (no album) ---
    suspend fun uploadReceipt(imageBytes: ByteArray): String? {
        return try {
            val requestFile = imageBytes.toRequestBody("image/jpeg".toMediaType())
            val body = MultipartBody.Part.createFormData("image", "receipt.jpg", requestFile)
            val response = imgBBService.uploadImage(MY_IMGBB_KEY, body)
            response.data.url
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- Transactions ---
    fun getAllTransactions(): Flow<List<Transaction>> = callbackFlow {
        val subscription = transactionsRef()
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val transactions = snapshot.documents.mapNotNull { doc ->
                        val type = doc.getString("type") ?: return@mapNotNull null
                        val itemName = doc.getString("itemName") ?: ""
                        val amount = doc.getDouble("amount") ?: 0.0
                        val quantity = (doc.getLong("quantity") ?: 1L).toInt()
                        val unitPrice = doc.getDouble("unitPrice") ?: amount
                        val createdBy = doc.getString("createdBy") ?: "Anonymous"
                        val paymentType = doc.getString("paymentType") ?: "Cash"
                        val receiptUrl = doc.getString("receiptUrl")
                        val timestamp = doc.getLong("timestamp") ?: 0L
                        val batchId = doc.getString("batchId") ?: ""

                        Transaction(
                            id = doc.id, type = type, itemName = itemName, amount = amount,
                            quantity = quantity, unitPrice = unitPrice, createdBy = createdBy,
                            paymentType = paymentType, receiptUrl = receiptUrl,
                            timestamp = timestamp, batchId = batchId
                        )
                    }
                    trySend(transactions)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun insert(transaction: Transaction) {
        val data = hashMapOf(
            "type" to transaction.type, "itemName" to transaction.itemName,
            "amount" to transaction.amount, "quantity" to transaction.quantity,
            "unitPrice" to transaction.unitPrice, "createdBy" to transaction.createdBy,
            "paymentType" to transaction.paymentType, "receiptUrl" to transaction.receiptUrl,
            "timestamp" to transaction.timestamp, "batchId" to transaction.batchId
        )
        transactionsRef().add(data)
    }

    fun delete(transactionId: String) {
        transactionsRef().document(transactionId).delete()
    }

    fun update(
        id: String, type: String, itemName: String, amount: Double,
        quantity: Int, unitPrice: Double, createdBy: String, paymentType: String, batchId: String
    ) {
        val updatedData = mapOf(
            "type" to type, "itemName" to itemName, "amount" to amount,
            "quantity" to quantity, "unitPrice" to unitPrice, "createdBy" to createdBy,
            "paymentType" to paymentType, "batchId" to batchId
        )
        transactionsRef().document(id).update(updatedData)
    }

    // --- Profiles ---
    fun getProfiles(): Flow<List<String>> = callbackFlow {
        val subscription = profilesRef()
            .orderBy("name")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val profiles = snapshot.documents.mapNotNull { it.getString("name") }
                    trySend(if (profiles.isEmpty()) listOf("Guest") else profiles)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun addProfile(name: String) { profilesRef().document(name).set(mapOf("name" to name)) }
    fun deleteProfile(name: String) { profilesRef().document(name).delete() }

    // --- Payment Types ---
    fun getPaymentTypes(): Flow<List<String>> = callbackFlow {
        val subscription = paymentTypesRef()
            .orderBy("name")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val types = snapshot.documents.mapNotNull { it.getString("name") }
                    trySend(if (types.isEmpty()) listOf("Cash") else types)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun addPaymentType(name: String) { paymentTypesRef().document(name).set(mapOf("name" to name)) }
    fun deletePaymentType(name: String) { paymentTypesRef().document(name).delete() }
}