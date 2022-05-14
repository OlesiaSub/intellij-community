// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.application.ApplicationManager
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
    //action.suspendContext!!.debugProcess.managerThread.invokeAndWait(object : DebuggerCommandImpl() {
    //  override fun action() {
    //    println("YEA IN ACTION")
    //    this.hold()
    //    println("IN ACTION WOW")
    //    this.release()
    //  }
    //})
    if (event is MethodExitEvent) {
      if (methods.contains(event.method())) { // костыльно
        methods.remove(event.method())
        return true
      }
      methods.add(event.method())
      handleMethodExitEvent(event)
      println("in handle ${event.method()}")
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
    println("init res types")
    toReturn = true
    terminationCallReached = true
    //this.SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL
    index++
    //val vm = event.virtualMachine()
    val vm = (mySession!!.currentStackFrame as JavaStackFrame).stackFrameProxy.virtualMachine
    val targetClass = vm.classesByName(targetClassName)[0]
    //(mySession!!.debugProcess as JavaDebugProcess).debuggerSession.process.managerThread.invoke(object : DebuggerCommandImpl() {
    //  override fun getPriority(): PrioritizedTask.Priority {
    //    return PrioritizedTask.Priority.HIGH
    //  }
    //
    //  override fun action() {
        println("hehehehe")
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
    }
    //else if (event.method().name().equals(chain.intermediateCalls.get(chain.intermediateCalls.size - 1).name)) {
    //  this.SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL
    //  toReturn = true
    //}
    else if (returnValue is ObjectReference) {
      //val runnableVal = runnable@{
        println("IN RUNNABLE ${event.method()}")
        //val targetClass = event.virtualMachine().classesByName(targetClassName)[0]
        val targetClass = (mySession!!.currentStackFrame as JavaStackFrame).stackFrameProxy.virtualMachine.classesByName(targetClassName)[0]
        //println("${event.thread()} THREADS11 ${(mySession!!.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference} CURRENT ${Thread.currentThread()}" +
        //        "MANAGER ${ (mySession!!.debugProcess as JavaDebugProcess).debuggerSession.process.managerThread}")

        //(mySession!!.debugProcess as JavaDebugProcess).debuggerSession.process.managerThread.invoke(object : DebuggerCommandImpl() {
        //  override fun getPriority(): PrioritizedTask.Priority {
        //    return PrioritizedTask.Priority.HIGH
        //  }
        //
        //  override fun action() {
            if (!initialized) {
              initialized = true;
              if (targetClass is ClassType) {
                val chainSize = chain.intermediateCalls.size + 1
                println("filtered req in INIT")
                targetClass.invokeMethod(
                  //(mySession!!.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference,
                  event.thread(),
                  targetClass.methodsByName("init")[0], // todo replace with constructor?
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
            println("ret val = $returnValue ${fieldValueByIndex}")
            val newReturnValue = returnValue.invokeMethod(
              //(mySession!!.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference,
              event.thread(),
              returnValue.referenceType().methodsByName("peek")[0],
              listOf(fieldValueByIndex!!),
              1)
            println("here in run $newReturnValue")
            event.thread().forceEarlyReturn(newReturnValue)
            //(mySession.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference.forceEarlyReturn(newReturnValue)
          //}
        //}
        //)
        //val chainSize = chain.intermediateCalls.size + 1
        //targetClass.invokeMethod((mySession!!.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference,
        //                         targetClass.methodsByName("init")[0], // todo replace with constructor?
        //                         listOf(stackFrame.stackFrameProxy.virtualMachine.mirrorOf(chainSize)),
        //                         0)

        //val field = targetClass.fieldByName("consumersArray")
        //val fieldValue = targetClass.getValues(listOf(field))[field]
        //var fieldValueByIndex: Value? = null
        //if (fieldValue is ArrayReference && index < fieldValue.values.size) {
        //  fieldValueByIndex = fieldValue.getValue(index)
        //  index++
        //}
        //val newReturnValue = returnValue
        //  .invokeMethod(event.thread(),
        //                returnValue.referenceType().methodsByName("peek")[0],
        //                listOf(fieldValueByIndex!!),
        //                0)
        //event.thread().forceEarlyReturn(newReturnValue)
        //(mySession!!.debugProcess as JavaDebugProcess).debuggerSession.process.managerThread.processRemaining()
      //  return@runnable
      //}
      //ApplicationManager.getApplication().invokeAndWait(runnableVal)
    }
  }
}
