// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import org.apache.commons.io.IOUtils

class ConsumerExtractor {
  fun extractConsumer(): ByteArray {
    val input = ConsumerExtractor::class.java.getResourceAsStream("MyConsumerTest.class")
    return IOUtils.toByteArray(input)
  }
}