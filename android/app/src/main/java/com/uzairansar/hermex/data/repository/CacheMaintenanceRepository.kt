package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.data.db.CacheDao

class CacheMaintenanceRepository(
    private val cacheDao: CacheDao,
) {
    suspend fun clearServer(serverUrl: String) {
        cacheDao.clearServer(serverUrl)
    }

    suspend fun maintenance(now: Long = System.currentTimeMillis()) {
        cacheDao.maintenance(now)
    }
}
