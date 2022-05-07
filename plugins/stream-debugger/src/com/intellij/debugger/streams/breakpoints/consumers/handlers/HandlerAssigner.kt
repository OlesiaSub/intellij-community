// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers

import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.intermediate.DistinctHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.AnyMatchHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.CollectHandler

object HandlerAssigner {
  // todo separate intermediate and terminal
  val handlersByName: MutableMap<String, Any> = mutableMapOf(
    "map" to BasicHandler(),
    "filter" to BasicHandler(),
    "distinct" to DistinctHandler(),
    "anyMatch" to AnyMatchHandler(),
    "count" to BasicHandler(),
    "collect" to CollectHandler()
  )
}