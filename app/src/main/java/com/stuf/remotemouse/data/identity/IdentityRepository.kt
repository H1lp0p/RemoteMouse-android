package com.stuf.remotemouse.data.identity

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.stuf.remotemouse.data.local.dao.MeDao
import com.stuf.remotemouse.data.local.entity.MeEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

data class DeviceFingerprint(
    val androidId: String,
    val model: String,
    val manufacturer: String,
    val osVersion: String,
)

@Singleton
class IdentityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meDao: MeDao,
) {
    fun observeMe(): Flow<MeEntity?> = meDao.observeMe()

    suspend fun ensureMe(): MeEntity {
        meDao.getMe()?.let { return it }
        val fingerprint = currentFingerprint()
        val me = MeEntity(
            clientId = UUID.randomUUID().toString(),
            clientName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidId = fingerprint.androidId,
            model = fingerprint.model,
            manufacturer = fingerprint.manufacturer,
            osVersion = fingerprint.osVersion,
        )
        meDao.upsert(me)
        return me
    }

    suspend fun updateClientName(name: String) {
        val me = ensureMe()
        meDao.upsert(me.copy(clientName = name.trim().ifEmpty { me.clientName }))
    }

    fun currentFingerprint(): DeviceFingerprint {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "unknown"
        return DeviceFingerprint(
            androidId = androidId,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            osVersion = Build.VERSION.RELEASE ?: "",
        )
    }
}
