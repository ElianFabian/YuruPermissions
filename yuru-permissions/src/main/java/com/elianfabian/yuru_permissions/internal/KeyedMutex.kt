package com.elianfabian.yuru_permissions.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal class KeyedMutex<K : Any> {
	private val locks = ConcurrentHashMap<K, Mutex>()

	suspend fun <T> withLock(key: K, block: suspend () -> T): T {
		val mutex = locks.getOrPut(key) { Mutex() }
		return mutex.withLock { block() }
	}
}
