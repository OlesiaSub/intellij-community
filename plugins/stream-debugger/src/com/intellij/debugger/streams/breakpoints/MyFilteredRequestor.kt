// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.sun.jdi.*
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent

class MyFilteredRequestor(project: Project,
                          private val stackFrame: JavaStackFrame,
                          private val chainsSize: Int) : FilteredRequestorImpl(project) {

  private var methods: MutableSet<Method> = mutableSetOf()
  private val targetClassName = "com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer"

  companion object {
    var index = 0
    var initialized = false
    var terminationCallReached = false
  }

  @Override
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent): Boolean {
    if (event is MethodExitEvent) {
      if (methods.contains(event.method())) { // костыльно
        methods.remove(event.method())
        return true
      }
      methods.add(event.method())
      handleMethodExitEvent(event)
    }
    return true
  }

  private fun handleMethodExitEvent(event: MethodExitEvent) {
    val returnValue = event.returnValue()
    if (index == chainsSize) {
      terminationCallReached = true
      index++
      val targetClass = event.virtualMachine().classesByName(targetClassName)[0]
      if (targetClass is ClassType) {
        targetClass.invokeMethod(event.thread(),
                                 targetClass.methodsByName("setReturnValue")[0],
                                 listOf(returnValue),
                                 0)
      }
    }
    if (returnValue is ObjectReference) {
      val runnableVal = runnable@{
        val targetClass = event.virtualMachine().classesByName(targetClassName)[0]
        if (!initialized) {
          initialized = true;
          if (targetClass is ClassType) {
            targetClass.invokeMethod(event.thread(),
                                     targetClass.methodsByName("init")[0], // todo replace with constructor?
                                     listOf(stackFrame.stackFrameProxy.virtualMachine.mirrorOf(chainsSize)),
                                     0)
          }
        }
        val field = targetClass.fieldByName("consumersArray")
        val fieldValue = targetClass.getValues(listOf(field))[field]
        var fieldValueByIndex: Value? = null
        if (fieldValue is ArrayReference) {
          fieldValueByIndex = fieldValue.getValue(index)
          index++
        }
        val newReturnValue = returnValue
          .invokeMethod(event.thread(),
                        returnValue.referenceType().methodsByName("peek")[0],
                        listOf(fieldValueByIndex!!),
                        0)
        event.thread().forceEarlyReturn(newReturnValue)
        return@runnable
      }
      ApplicationManager.getApplication().invokeLater(runnableVal)
    }
  }
}
