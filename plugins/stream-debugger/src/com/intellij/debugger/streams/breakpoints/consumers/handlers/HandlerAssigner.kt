// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers

import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.intermediate.DistinctHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.match.AllElementsMatchHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.match.AnyMatchHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.CollectHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.OptionalResultHandler

object HandlerAssigner {
  val intermediateHandlersByName: MutableMap<String, Any> = mutableMapOf(
    "map" to BasicHandler(),
    "mapToInt" to BasicHandler(),
    "mapToDouble" to BasicHandler(),
    "mapToLong" to BasicHandler(),
    "mapToObj" to BasicHandler(),
    "flatMap" to BasicHandler(),
    "flatMapToInt" to BasicHandler(),
    "flatMapToDouble" to BasicHandler(),
    "flatMapToLong" to BasicHandler(),
    "filter" to BasicHandler(),
    "peek" to BasicHandler(),
    "peekFirst" to BasicHandler(),
    "peekLast" to BasicHandler(),
    "mapToEntry" to BasicHandler(),
    "peekKeys" to BasicHandler(),
    "peekValues" to BasicHandler(),
    "peekKeyValue" to BasicHandler(),
    "sorted" to BasicHandler(),
    "sortedBy" to BasicHandler(),
    "reverseSorted" to BasicHandler(),
    "sortedByInt" to BasicHandler(),
    "sortedByDouble" to BasicHandler(),
    "sortedByLong" to BasicHandler(),
    "dropWhile" to BasicHandler(),
    "takeWhile" to BasicHandler(),
    "skip" to BasicHandler(),
    "limit" to BasicHandler(),
    "parallel" to BasicHandler(),
    "onClose" to BasicHandler(),
    "boxed" to BasicHandler(),
    "prepend" to BasicHandler(),
    "headTail" to BasicHandler(),
    "distinct" to DistinctHandler()
  )

  val terminalHandlersByName: MutableMap<String, Any> = mutableMapOf(
    "count" to BasicHandler(),
    "sum" to BasicHandler(),
    "forEach" to BasicHandler(),
    "forEachOrdered" to BasicHandler(),
    "anyMatch" to AnyMatchHandler(),
    "allMatch" to AllElementsMatchHandler(),
    "noneMatch" to AllElementsMatchHandler(),
    "collect" to CollectHandler(),
    "toArray" to CollectHandler(),
    "max" to OptionalResultHandler(),
    "min" to OptionalResultHandler(),
    "findAny" to OptionalResultHandler(),
    "findFirst" to OptionalResultHandler()
  )
}