// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.streams.breakpoints.consumers.handlers.HandlerAssigner
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.sun.jdi.*
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent
import java.util.stream.IntStream

class MyFilteredRequestor(project: Project,
                          private val stackFrame: JavaStackFrame,
                          private val chain: StreamChain) : FilteredRequestorImpl(project) {

  private var methods: MutableSet<Method> = mutableSetOf()
  private val targetClassName = "com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer"

  companion object {
    var index = 0
    var chainMethodIndex = 0
    var initialized = false
    var terminationCallReached = false
  }

  lateinit var charArray: Array<Char>

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

  private fun getParametersList(returnValue: Value, vm: VirtualMachine): MutableList<ArrayReference?> {
    var classes: List<ReferenceType>
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
        println("ELSE") // todo обработать
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
    terminationCallReached = true
    index++
    //println("here i++")
    val vm = event.virtualMachine()
    val targetClass = vm.classesByName(targetClassName)[0]
    if (targetClass is ClassType) {
      targetClass.invokeMethod(event.thread(),
                               targetClass.methodsByName("setReturnValue")[0],
                               getParametersList(returnValue, vm),
                               0)
    }
  }

  private fun handleMethodExitEvent(event: MethodExitEvent) {
    val returnValue = event.returnValue()
    if (event.method().name().equals(chain.terminationCall.name)) {
      initializeResultTypes(event, returnValue)
    }
    else if (returnValue is ObjectReference
             && ((chainMethodIndex < chain.intermediateCalls.size && event.method().name().equals(
        chain.intermediateCalls.get(chainMethodIndex).name))
                 || (chainMethodIndex == chain.intermediateCalls.size && event.method().name().equals(
        chain.terminationCall.name)) || (event.method().name().equals("stream")))) {
      if (initialized && (event.method().name().equals("stream"))) return
      if (!event.method().name().equals("stream")) {
        chainMethodIndex++
      }
      val runnableVal = runnable@{
        val targetClass = event.virtualMachine().classesByName(targetClassName)[0]
        if (!initialized) {
          initialized = true;
          if (targetClass is ClassType) {
            val chainSize = chain.intermediateCalls.size + 1
            val classes = event.virtualMachine().classesByName("char[]")
            val arrayType = classes[0] as ArrayType
            val arrayInstance = arrayType.newInstance(chainSize + 1)
            charArray = Array(chainSize + 2) { '.' }
            var lastStreamType = '.'
            var idx = 0
            chain.intermediateCalls.forEachIndexed { index, intermediateStreamCall ->
              run {
                val valueToSet = event.virtualMachine().mirrorOf(lastStreamType)
                arrayInstance.setValue(index, valueToSet)
                charArray.set(index, lastStreamType)
                idx = index
                if (HandlerAssigner.streamTypeByName.contains(intermediateStreamCall.name)
                    && lastStreamType != HandlerAssigner.streamTypeByName.get(intermediateStreamCall.name)) {
                  lastStreamType = HandlerAssigner.streamTypeByName.get(intermediateStreamCall.name)!!
                }
              }
            }
            val valueToSet = event.virtualMachine().mirrorOf(lastStreamType)
            if (idx == 0) idx = -1
            arrayInstance.setValue(idx + 1, valueToSet)
            charArray.set(idx + 1, lastStreamType)
            arrayInstance.setValue(idx + 2, valueToSet)
            charArray.set(idx + 2, '.')
            val listParam = mutableListOf(arrayInstance)
            //println("chains siez " + chainSize)
            targetClass.invokeMethod(event.thread(),
                                     targetClass.methodsByName("init")[0],
                                     listParam,
                                     0)
          }
        }
        val field = targetClass.fieldByName("consumersArray")
        val fieldValue = targetClass.getValues(listOf(field))[field]
        if (fieldValue is ArrayReference && index < fieldValue.values.size) {
          //println("i++")
          index++
        }
        var valueToReturn = returnValue
        if (event.method().name().equals("parallel")) {
          valueToReturn = returnValue
            .invokeMethod(event.thread(),
                          returnValue.referenceType().methodsByName("sequential")[0],
                          listOf(),
                          0)
        }
        var castValue: Value
        //println(event.method().name())
        //println("current index " + index)
        //if (index < charArray.size) println("IDDXX ${charArray[index]}")
        if (charArray[index - 1] == 'i') {
          castValue = (targetClass as ClassType).invokeMethod(event.thread(),
                                                              targetClass.methodsByName("getIntConsumer")[0],
                                                              listOf(event.virtualMachine().mirrorOf(index - 1)),
                                                              0)
        }
        else {
          castValue = (targetClass as ClassType).invokeMethod(event.thread(),
                                                              targetClass.methodsByName("getConsumer")[0],
                                                              listOf(event.virtualMachine().mirrorOf(index - 1)),
                                                              0)
        }
        val newReturnValue = (valueToReturn as ObjectReference)
          .invokeMethod(event.thread(),
                        returnValue.referenceType().methodsByName("peek")[0],
                        listOf(castValue),
                        0)

        event.thread().forceEarlyReturn(newReturnValue)
        return@runnable
      }
      ApplicationManager.getApplication().invokeLater(runnableVal)
    }
  }
}
