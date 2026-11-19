package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?,
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun translateErrorToBengali(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }
    val fsEx = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (fsEx != null) {
        return when (fsEx.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "অনুমতি নেই (PERMISSION_DENIED)। শুধুমাত্র অনুমোদিত মালিক বা কর্মচারী এই কাজটি করতে পারবেন।"
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                "Firebase Spark ফ্রি প্ল্যানের দৈনিক কোটা সীমা পূর্ণ হয়েছে। পরদিন কোটা রিসেট হবে — কোনো পেইড আপগ্রেড চালু করা হয়নি।"
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না। সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।"
            FirebaseFirestoreException.Code.ABORTED ->
                "একই সময়ে আরেকটি লেনদেন হওয়ায় সংঘর্ষ হয়েছে। অনুগ্রহ করে পুনরায় চেষ্টা করুন।"
            else -> fsEx.localizedMessage ?: "ডেটাবেস ত্রুটি হয়েছে।"
        }
    }
    val msg = error.localizedMessage ?: error.message ?: "অজানা ত্রুটি হয়েছে।"
    if (msg.contains("Quota exceeded", ignoreCase = true)) {
        return "Firebase Spark ফ্রি প্ল্যানের দৈনিক রিড/রাইট কোটা সীমা পূর্ণ হয়েছে। পরদিন রিসেট হবে।"
    }
    return msg
}
