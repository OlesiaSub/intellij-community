// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.sun.jdi.*
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent

class MyMethodBreakpoint(project: Project,
                         breakpoint: XBreakpoint<out XBreakpointProperties<*>>?,
                         private val contextImpl: EvaluationContextImpl,
                         private val process: DebugProcessImpl,
                         private val stackFrame: JavaStackFrame) : MethodBreakpoint(project, breakpoint) {

  var sett: MutableSet<Method> = mutableSetOf()

  @Override
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent): Boolean {
    if (event is MethodExitEvent) {
      println("method " + event.method())
      if (sett.contains(event.method())) { // костыль
        sett.remove(event.method())
        return true
      }
      sett.add(event.method())
      println("ret val " + event.returnValue())
      handleMethodExitEvent(event)
    }
    return true
  }

  private fun handleMethodExitEvent(event: MethodExitEvent) {
    val returnValue = event.returnValue()
    if (returnValue is ObjectReference) {
      val runnableVal = runnable@{
        // class loading
        var field: Field
        var fieldValue: Value? = null
        val classLoadingUtil = CustomClassLoadingUtil(contextImpl, (stackFrame.descriptor.debugProcess as DebugProcessImpl), stackFrame)
        val loader = classLoadingUtil.loadClass("com.intellij.debugger.streams.breakpoints.MyConsumerTest", event)
        println("DEBUG classes")
        if (loader == null) return@runnable
        loader.definedClasses().forEach { println(it) }
        loader.definedClasses().forEach {
          if (it.name().contains("MyConsumerTest")) {
            field = it.fieldByName("consumer")
            fieldValue = it.getValues(listOf(field)).get(field)
          }
        }
        // consumer that is present in the project
        //val field = event.virtualMachine().classesByName("StreamsTest")[0].fieldByName("consumerr")
        //val fieldValue = event.virtualMachine().classesByName("StreamsTest")[0].getValues(listOf(field)).get(field)
        val ret = returnValue
          .invokeMethod(event.thread(),
                        returnValue.referenceType().methodsByName("peek")[0],
                        listOf(fieldValue),
                        0)
        event.thread().forceEarlyReturn(ret)
        //process.virtualMachineProxy.classesByName()
        return@runnable
        //event.request().disable()
      }
      ApplicationManager.getApplication().invokeLater(runnableVal)
    }
  }
}
