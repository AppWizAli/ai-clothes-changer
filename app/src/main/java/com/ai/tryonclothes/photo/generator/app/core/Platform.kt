package com.ai.tryonclothes.photo.generator.app.core

import java.util.UUID

fun interface Clock { fun now(): Long }

fun interface IdGenerator { fun newId(): String }

object SystemClock : Clock { override fun now(): Long = System.currentTimeMillis() }

object UuidGenerator : IdGenerator { override fun newId(): String = UUID.randomUUID().toString() }
