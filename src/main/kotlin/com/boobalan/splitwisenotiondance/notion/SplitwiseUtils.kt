package com.boobalan.splitwisenotiondance.notion

import com.boobalan.splitwisenotiondance.log
import kotlinx.coroutines.delay

class SplitwiseUtils {

    companion object {
        suspend fun <T> retryIO(delay: Long = 100L, maximumDelay: Long = 60_000L, block: suspend () -> T): T {

            var curDelay = delay
            var count = 0
            while (count++ < 5) {  // max 5 attempts
                try {
                    return block()
                } catch (e: Exception) {
                    log.error { e }
                }
                delay(curDelay)
                curDelay = (curDelay * 2).coerceAtMost(maximumDelay)
            }
            throw IllegalAccessException("trying to call API more than allowed limit")
        }
    }

}