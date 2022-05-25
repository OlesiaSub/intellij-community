// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.debugger.impl.InvokeThread
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.*
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent

class MyFilteredRequestor(project: Project,
                          private val stackFrame: JavaStackFrame,
                          private val chain: StreamChain,
                          private val mySession: XDebugSession?,
                          private val process: DebugProcessImpl) : FilteredRequestorImpl(project) {

  private var methods: MutableSet<Method> = mutableSetOf()
  private val targetClassName = "com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer"

  companion object {
    var toReturn = false
    var index = 0
    var initialized = false
    var terminationCallReached = false
  }

  @Override
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent): Boolean {
    if (event is MethodExitEvent) {
      if (methods.contains(event.method())) {
        methods.remove(event.method())
        return true
      }
      methods.add(event.method())
      handleMethodExitEvent(event)
    }
    return true
  }

  private fun getParametersList(returnValue: Value, vm: VirtualMachineProxy): MutableList<ArrayReference?> {
    val classes: List<ReferenceType>
    when (returnValue) {
      is IntegerValue -> {
        classes = vm.classesByName("int[]")
      }
      is LongValue -> {
        classes = vm.classesByName("long[]")
      }
      is BooleanValue -> {
        classes = vm.classesByName("boolean[]")
      }
      is DoubleValue -> {
        classes = vm.classesByName("double[]")
      }
      is FloatValue -> {
        classes = vm.classesByName("float[]")
      }
      is ShortValue -> {
        classes = vm.classesByName("short[]")
      }
      is ByteValue -> {
        classes = vm.classesByName("byte[]")
      }
      is CharValue -> {

        classes = vm.classesByName("char[]")
      }
      is VoidValue -> {
        return mutableListOf(null)
      }
      is ObjectReference -> {
        classes = vm.classesByName("java.lang.Object[]")
      }
      else -> {
        classes = listOf()
      }
    }
    if (classes.size != 1 || classes[0] !is ArrayType) {
      return mutableListOf()
    }
    val arrayType = classes[0] as ArrayType
    val arrayInstance = arrayType.newInstance(1)
    arrayInstance.setValue(0, returnValue)
    return mutableListOf(arrayInstance)
  }

  private fun initializeResultTypes(event: MethodExitEvent, returnValue: Value) {
    toReturn = true
    terminationCallReached = true
    index++
    val vm = (mySession!!.currentStackFrame as JavaStackFrame).stackFrameProxy.virtualMachine
    val targetClass = vm.classesByName(targetClassName)[0]
    //(mySession!!.debugProcess as JavaDebugProcess).debuggerSession.process.managerThread.invoke(object : DebuggerCommandImpl() {
    //  override fun getPriority(): PrioritizedTask.Priority {
    //    return PrioritizedTask.Priority.HIGH
    //  }
    //
    //  override fun action() {
    if (targetClass is ClassType) {
      targetClass.invokeMethod(
        event.thread(),
        //(mySession!!.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference,
        targetClass.methodsByName("setReturnValue")[0],
        getParametersList(returnValue, vm),
        0)
    }
    //  }
    //})
    //(mySession!!.debugProcess as JavaDebugProcess).debuggerSession.process.managerThread.processRemaining()
    //if (targetClass is ClassType) {
    //  targetClass.invokeMethod(event.thread(),
    //                           targetClass.methodsByName("setReturnValue")[0],
    //                           getParametersList(returnValue, vm),
    //                           0)
    //}
  }

  private fun handleMethodExitEvent(event: MethodExitEvent) {
    val returnValue = event.returnValue()
    if (event.method().name().equals(chain.terminationCall.name)) {
      initializeResultTypes(event, returnValue)
      this.SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL
      toReturn = true
    }
    else if (returnValue is ObjectReference) {
      //val runnableVal = runnable@{
      val targetClass = (mySession!!.currentStackFrame as JavaStackFrame).stackFrameProxy.virtualMachine.classesByName(targetClassName)[0]
      val request = InvokeThread.getCurrentThreadRequest().owner.schedule(object : DebuggerCommandImpl() {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun action() {
          if (!initialized) {
            initialized = true;
            if (targetClass is ClassType) {
              val chainSize = chain.intermediateCalls.size + 1
              targetClass.invokeMethod(
                (mySession.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference,
                targetClass.methodsByName("init")[0],
                listOf(stackFrame.stackFrameProxy.virtualMachine.mirrorOf(chainSize)),
                0)
            }
          }
          val field = targetClass.fieldByName("consumersArray")
          val fieldValue = targetClass.getValues(listOf(field))[field]
          var fieldValueByIndex: Value? = null
          if (fieldValue is ArrayReference && index < fieldValue.values.size) {
            fieldValueByIndex = fieldValue.getValue(index)
            index++
          }
          val newReturnValue = returnValue.invokeMethod(
            (mySession.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference,
            //event.thread(),
            returnValue.referenceType().methodsByName("peek")[0],
            listOf(fieldValueByIndex!!),
            1)
          event.thread().forceEarlyReturn(newReturnValue)
          //(mySession.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference.forceEarlyReturn(newReturnValue)
        }
      }
      )
      //(mySession!!.debugProcess as JavaDebugProcess).debuggerSession.process.managerThread.processRemaining()
      //  return@runnable
      //}
      //ApplicationManager.getApplication().invokeAndWait(runnableVal)
    }
  }
}
