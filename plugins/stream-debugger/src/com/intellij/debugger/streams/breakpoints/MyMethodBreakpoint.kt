// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.CommonClassNames
import com.intellij.util.containers.ContainerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.jetbrains.jdi.ClassLoaderReferenceImpl
import com.jetbrains.jdi.MethodImpl
import com.sun.jdi.*
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent

class MyMethodBreakpoint(project: Project,
                         breakpoint: XBreakpoint<out XBreakpointProperties<*>>?,
                         private val cimpl: EvaluationContextImpl,
                         private val process: DebugProcessImpl,
                         private val stackFrame: JavaStackFrame) : MethodBreakpoint(project, breakpoint) {

  var sett: MutableSet<Method> = mutableSetOf()

  @Override
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent): Boolean {
    if (event is MethodExitEvent) {
      println("method " + event.method())
      sett.forEach { println(it) }
      if (sett.contains(event.method())) {
        sett.remove(event.method())
        return true
      }
      sett.add(event.method())
      println("ret val " + event.returnValue())
      handleMethodExitEvent(event)
      //event.request().disable()
    }
    return true
  }

  private fun handleMethodExitEvent(event: MethodExitEvent) {
    val returnValue = event.returnValue()
    if (returnValue is ObjectReference) {
      val runnableVal = runnable@{
        var field: Field
        var fieldValue: Value? = null
        //try {
        //  //val loader = event.method().declaringType().classLoader()
        //    process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
        //                                                                       stackFrame.stackFrameProxy.threadProxy()) {
        //      override fun getPriority(): PrioritizedTask.Priority {
        //        return PrioritizedTask.Priority.HIGH
        //      }
        //
        //      override fun threadAction(suspendContext: SuspendContextImpl) {
        //val loader = ClassLoadingUtils.getClassLoader(cimpl, process)
        val classLoadingUtil = CustomClassLoadingUtil(cimpl, (stackFrame.descriptor.debugProcess as DebugProcessImpl), stackFrame)
        val loader = classLoadingUtil.loadClass("com.intellij.debugger.streams.breakpoints.MyConsumerTest", event)
        println("classes hihihi")
        loader!!.definedClasses().forEach { println(it) }
        //    }
        //  })
        //} catch (e: VMDisconnectedException) {
        //  println("Virtual Machine is disconnected.")
        //}
        //catch (e: Exception) {
        //  e.printStackTrace()
        //}
        //loadClass("com.intellij.debugger.streams.breakpoints.MyConsumerTest", loader, event)
        //println("classes!!!")
        //event.virtualMachine().allClasses().forEach { println(it) }
        //val meth = event.virtualMachine().classesByName("java.io.PrintStream").get(0).methodsByName("println").get(9)
        //Class.forName("ToBeLoaded", true, event.virtualMachine().allClasses().get(0).classLoader())
        //val field = event.virtualMachine().classesByName("com.intellij.debugger.streams.breakpoints.MyConsumerTest")[0].fieldByName("consumer")
        //val fieldValue = event.virtualMachine().classesByName("com.intellij.debugger.streams.breakpoints.MyConsumerTest")[0].getValues(listOf(field)).get(field)
        //val field = event.virtualMachine().classesByName("StreamsTest")[0].fieldByName("consumerr")
        //val fieldValue = event.virtualMachine().classesByName("StreamsTest")[0].getValues(listOf(field)).get(field)
        //process.virtualMachineProxy.allClasses().forEach { println(it) }
        //val field = process.virtualMachineProxy.classesByName("com.intellij.debugger.streams.breakpoints.MyConsumerTest")[0].fieldByName("consumer")
        //val fieldValue = process.virtualMachineProxy.classesByName("com.intellij.debugger.streams.breakpoints.MyConsumerTest")[0].getValues(listOf(field)).get(field)
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
      //catch (e: Exception) {
      //  e.printStackTrace()
      //}
      ApplicationManager.getApplication().invokeLater(runnableVal)
    }
  }
}
