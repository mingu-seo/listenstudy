package com.codro.listenstudy.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudKeySavePolicyTest {
    @Test
    fun `successful save is stamped with the request id`() {
        val resolution = CloudKeySavePolicy.resolve(requestId = 42L, isBlank = false, saved = true)

        assertEquals(CloudKeySaveResult(42L, CloudKeySaveOutcome.Success), resolution.result)
        assertEquals("Google Cloud API 키를 비공개 앱 저장소에 저장했습니다.", resolution.message)
    }

    @Test
    fun `failed save reports failure for the same request rather than staying silent`() {
        val resolution = CloudKeySavePolicy.resolve(requestId = 42L, isBlank = false, saved = false)

        assertEquals(CloudKeySaveResult(42L, CloudKeySaveOutcome.Failure), resolution.result)
        assertEquals("API 키를 안전하게 저장하지 못했습니다. 다시 시도해 주세요.", resolution.message)
    }

    @Test
    fun `blank input is rejected as a failure with its own message`() {
        val resolution = CloudKeySavePolicy.resolve(requestId = 9L, isBlank = true, saved = false)

        assertEquals(CloudKeySaveResult(9L, CloudKeySaveOutcome.Failure), resolution.result)
        assertEquals("입력한 API 키가 비어 있습니다.", resolution.message)
    }

    @Test
    fun `blank input never counts as success even if storage claims it saved`() {
        val resolution = CloudKeySavePolicy.resolve(requestId = 9L, isBlank = true, saved = true)

        assertEquals(CloudKeySaveOutcome.Failure, resolution.result.outcome)
    }

    @Test
    fun `a resolved result never carries the reserved no-request id`() {
        val resolution = CloudKeySavePolicy.resolve(requestId = 1L, isBlank = false, saved = true)

        assertFalse(resolution.result.requestId == CloudKeySaveResult.NO_REQUEST)
    }
}
