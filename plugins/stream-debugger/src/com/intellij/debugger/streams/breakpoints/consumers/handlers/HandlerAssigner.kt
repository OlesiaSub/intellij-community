// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers

import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.intermediate.DistinctHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.MatchHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.CollectHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.OptionalResultHandler

object HandlerAssigner {
  val intermediateHandlersByName: MutableMap<String, Any> = mutableMapOf(
    "map" to BasicHandler(),
    "mapToInt" to BasicHandler(),
    "mapToDouble" to BasicHandler(),
    "mapToLong" to BasicHandler(),
    "flatMap" to BasicHandler(),
    "flatMapToInt" to BasicHandler(),
    "flatMapToDouble" to BasicHandler(),
    "flatMapToLong" to BasicHandler(),
    "filter" to BasicHandler(),
    "peek" to BasicHandler(),
    "sorted" to BasicHandler(),
    "dropWhile" to BasicHandler(),
    "takeWhile" to BasicHandler(),
    "skip" to BasicHandler(),
    "limit" to BasicHandler(),
    "parallel" to BasicHandler(), // todo переделывает в seq
    "onClose" to BasicHandler(),
    "boxed" to BasicHandler(),
    "distinct" to DistinctHandler()
  )

  val terminalHandlersByName: MutableMap<String, Any> = mutableMapOf(
    "count" to BasicHandler(),
    "sum" to BasicHandler(),
    "forEach" to BasicHandler(),
    "forEachOrdered" to BasicHandler(),
    "anyMatch" to MatchHandler(),
    "allMatch" to MatchHandler(),
    "noneMatch" to MatchHandler(),
    "collect" to CollectHandler(),
    "toArray" to CollectHandler(),
    "max" to OptionalResultHandler(),
    "min" to OptionalResultHandler(),
    "findAny" to OptionalResultHandler(),
    "findFirst" to OptionalResultHandler()
  )
}