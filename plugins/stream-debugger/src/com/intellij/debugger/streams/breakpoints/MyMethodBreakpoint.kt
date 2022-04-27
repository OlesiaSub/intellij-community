// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.sun.jdi.ArrayReference
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent
import java.util.concurrent.atomic.AtomicInteger

class MyMethodBreakpoint(project: Project,
                         breakpoint: XBreakpoint<out XBreakpointProperties<*>>?,
                         private val process: DebugProcessImpl,
                         private val stackFrame: JavaStackFrame) : MethodBreakpoint(project, breakpoint) {

  var methods: MutableSet<Method> = mutableSetOf()
  companion object var index = 0

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
    if (returnValue is ObjectReference) {
      val runnableVal = runnable@{
        //var flag = false
        //event.virtualMachine().allClasses().forEach {
        //  if (it.name().contains("MyConsumerTest")) {
        //    flag = true
        //  }
        //}
        //if (!flag) {
          //val contextImpl = EvaluationContextImpl(action.suspendContext as SuspendContextImpl, stackFrame.stackFrameProxy)
          //val classLoadingUtil = MyClassLoadingUtil(contextImpl, (stackFrame.descriptor.debugProcess as DebugProcessImpl), stackFrame)
          //classLoadingUtil.loadClass("com.intellij.debugger.streams.breakpoints.MyConsumerTest", event)
        //}
        val targetClass = event.virtualMachine().classesByName("com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer")[0]
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
        //if (newReturnValue is ArrayReference) {
        //  for (map in newReturnValue.values) {
        //    if (map is ArrayReference) {
        //      for (mapValue in map.values) {
        //        println("here")
        //      }
        //    }
        //  }
        //}
        return@runnable
      }
      ApplicationManager.getApplication().invokeLater(runnableVal)
    }
  }
}
