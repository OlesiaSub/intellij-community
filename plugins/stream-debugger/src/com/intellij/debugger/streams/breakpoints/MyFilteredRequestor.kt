// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.sun.jdi.*
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent

class MyFilteredRequestor(project: Project,
                          private val stackFrame: JavaStackFrame,
                          private val chain: StreamChain) : FilteredRequestorImpl(project) {

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

  private fun getParametersList(returnValue: Value, vm: VirtualMachine): MutableList<Value> {
    val typeIndex: Int
    val defaultInteger = vm.mirrorOf(0)
    val defaultLong = vm.mirrorOf(0L)
    val defaultChar = vm.mirrorOf(' ')
    val defaultBoolean = vm.mirrorOf(true)
    val defaultDouble = vm.mirrorOf(1.0)
    val defaultFloat = vm.mirrorOf(1.0f)
    val defaultVoid = vm.mirrorOfVoid();
    when (returnValue) {
      is IntegerValue -> {
        typeIndex = 0
        return mutableListOf(vm.mirrorOf(typeIndex), returnValue, defaultLong, defaultChar, defaultBoolean, defaultDouble, defaultFloat)
      }
      is LongValue -> {
        typeIndex = 1
        return mutableListOf(vm.mirrorOf(typeIndex), defaultInteger, returnValue, defaultChar, defaultBoolean, defaultDouble, defaultFloat)
      }
      is CharValue -> {
        typeIndex = 2
        return mutableListOf(vm.mirrorOf(typeIndex), defaultInteger, defaultLong, returnValue, defaultBoolean, defaultDouble, defaultFloat)
      }
      is BooleanValue -> {
        typeIndex = 3
        return mutableListOf(vm.mirrorOf(typeIndex), defaultInteger, defaultLong, defaultChar, returnValue, defaultDouble, defaultFloat)
      }
      is DoubleValue -> {
        typeIndex = 4
        return mutableListOf(vm.mirrorOf(typeIndex), defaultInteger, defaultLong, defaultChar, defaultBoolean, returnValue, defaultFloat)
      }
      is FloatValue -> {
        typeIndex = 5
        return mutableListOf(vm.mirrorOf(typeIndex), defaultInteger, defaultLong, defaultChar, defaultBoolean, defaultDouble, returnValue)
      }
      else -> {
        println("IN ELSE")
      }
    }
    // todo byte, void, string, object
    return mutableListOf()
  }

  private fun initializeResultTypes(event: MethodExitEvent, returnValue: Value) {
    terminationCallReached = true
    index++
    val vm = event.virtualMachine()
    val targetClass = vm.classesByName(targetClassName)[0]
    if (targetClass is ClassType) {
      if (returnValue is ObjectReference) {
        targetClass.invokeMethod(event.thread(),
                                 targetClass.methodsByName("setRetValueObject")[0],
                                 listOf(returnValue),
                                 0)
      } else {
        targetClass.invokeMethod(event.thread(),
                                 targetClass.methodsByName("setRetValue")[0],
                                 getParametersList(returnValue, vm),
                                 0)
      }
    }
  }

  private fun handleMethodExitEvent(event: MethodExitEvent) {
    val returnValue = event.returnValue()
    if (event.method().name().equals(chain.terminationCall.name)) {
      initializeResultTypes(event, returnValue)
    }
    if (returnValue is ObjectReference) {
      val runnableVal = runnable@{
        val targetClass = event.virtualMachine().classesByName(targetClassName)[0]
        if (!initialized) {
          initialized = true;
          if (targetClass is ClassType) {
            val chainSize = chain.intermediateCalls.size + 1
            targetClass.invokeMethod(event.thread(),
                                     targetClass.methodsByName("init")[0], // todo replace with constructor?
                                     listOf(stackFrame.stackFrameProxy.virtualMachine.mirrorOf(chainSize)),
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
