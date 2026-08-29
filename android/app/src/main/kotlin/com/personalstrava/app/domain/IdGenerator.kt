package com.personalstrava.app.domain

import java.util.UUID

/** Client-generated UUIDs are what make sync idempotent end to end (spec section 15/19). */
object IdGenerator {
    fun newActivityId(): String = UUID.randomUUID().toString()
    fun newPhotoId(): String = UUID.randomUUID().toString()
}
