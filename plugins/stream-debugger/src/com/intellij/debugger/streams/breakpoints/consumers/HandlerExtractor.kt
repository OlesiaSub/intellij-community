// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers

import org.apache.commons.io.IOUtils

class HandlerExtractor {
  fun extractConsumer(className: String): ByteArray {
    val input = HandlerExtractor::class.java.getResourceAsStream(className)
    return IOUtils.toByteArray(input)
  }
}