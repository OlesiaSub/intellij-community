// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
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
      val contextImpl = EvaluationContextImpl(action.suspendContext as SuspendContextImpl,
                                              stackFrame.stackFrameProxy)
      handleMethodExitEvent(event, action)
    }
    return true
  }

  private fun handleMethodExitEvent(event: MethodExitEvent, action: SuspendContextCommandImpl) {
    val returnValue = event.returnValue()
    if (returnValue is ObjectReference) {
      val runnableVal = runnable@{
        //try {
        //val loader = event.method().declaringType().classLoader()
        //process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
        //                                                                   stackFrame.stackFrameProxy.threadProxy()) {
        //  override fun getPriority(): PrioritizedTask.Priority {
        //    return PrioritizedTask.Priority.HIGH
        //  }
        //
        //  override fun threadAction(suspendContext: SuspendContextImpl) {
        //val loader = ClassLoadingUtils.getClassLoader(cimpl, process)
        var flag = false
        event.virtualMachine().allClasses().forEach {
          if (it.name().contains("MyConsumerTest")) {
            println("name matched here")
            flag = true
          }
        }
        if (!flag) {
          println("name did not match")
          //val contextImpl = EvaluationContextImpl(action.suspendContext as SuspendContextImpl, stackFrame.stackFrameProxy)
          //val classLoadingUtil = MyClassLoadingUtil(contextImpl, (stackFrame.descriptor.debugProcess as DebugProcessImpl), stackFrame)
          //classLoadingUtil.loadClass("com.intellij.debugger.streams.breakpoints.MyConsumerTest", event)
        }
        //}
        //  })
        //}
        //catch (e: VMDisconnectedException) {
        //  println("Virtual Machine is disconnected.")
        //}
        //catch (e: Exception) {
        //  e.printStackTrace()
        //}
        //val classLoader = ClassLoadingUtils.getClassLoader(contextImpl, process)
        //val classLoadingUtil = MyClassLoadingUtil(contextImpl, (stackFrame.descriptor.debugProcess as DebugProcessImpl), stackFrame)
        ////classLoadingUtil.loadClass("com.intellij.debugger.streams.breakpoints.MyConsumerTest", loader, event)
        //val classLoader = classLoadingUtil.loadClass("com.intellij.debugger.streams.breakpoints.MyConsumerTest", event)
        //println("classes hihihi")
        //classLoader!!.definedClasses().forEach { println(it) }
        var field: Field
        var fieldValue: Value? = null
        var found = false
        event.virtualMachine().allClasses().forEach {
          if (it.name().contains("MyConsumerTest") && !found) {
            println("name matched!")
            found = true
            field = it.fieldByName("consumer")
            fieldValue = it.getValues(listOf(field))[field]
          }
        }
        val ret = returnValue
          .invokeMethod(event.thread(),
                        returnValue.referenceType().methodsByName("peek")[0],
                        listOf(fieldValue),
                        0)
        event.thread().forceEarlyReturn(ret)
        // class loading
        //val classLoadingUtil = MyClassLoadingUtil(contextImpl, (stackFrame.descriptor.debugProcess as DebugProcessImpl), stackFrame)
        //val classLoader = classLoadingUtil.loadClass("com.intellij.debugger.streams.breakpoints.MyConsumerTest", event)
        //classLoader!!.definedClasses().forEach { println(it) }
        //// consumer that is present in the project
        //val field = event.virtualMachine().classesByName("StreamsTest")[0].fieldByName("consumerr")
        //val fieldValue = event.virtualMachine().classesByName("StreamsTest")[0].getValues(listOf(field)).get(field)
        //process.virtualMachineProxy.classesByName()
        return@runnable
        //event.request().disable()
      }
      ApplicationManager.getApplication().invokeLater(runnableVal)
    }
  }
}
