// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.streams.breakpoints.consumers.handlers.HandlerAssigner
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.*
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.request.MethodExitRequest

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
  lateinit var req1: MethodExitRequest
  lateinit var req2: MethodExitRequest
  lateinit var req3: MethodExitRequest
  lateinit var req4: MethodExitRequest
  private var toReturn: Boolean = false

  @Override
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent): Boolean {
    if (toReturn) {
      return false
    }
    if (event is MethodExitEvent) {
      println(event.method().name())
      if (methods.contains(event.method())) { // костыльно
        methods.remove(event.method())
        return toReturn
      }
      methods.add(event.method())
      req1.disable()
      req2.disable()
      req3.disable()
      req4.disable()
      handleMethodExitEvent(event)
      req1.enable()
      req2.enable()
      req3.enable()
      req4.enable()
    }
    return toReturn
  }

  private fun getParametersList(returnValue: Value, vm: VirtualMachine): MutableList<ArrayReference?> {
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
    if (event.method().name().equals(chain.terminationCall.name) && event.method().arguments().size == chain.terminationCall.arguments.size) {
      initializeResultTypes(event, returnValue)
      req1.disable()
      req2.disable()
      req3.disable()
      req4.disable()
      toReturn = true
    } else if (event.method().name().equals(chain.terminationCall.name)) {
      return
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
      //val runnableVal = runnable@{
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
              charArray[index] = lastStreamType
              idx = index
              if (HandlerAssigner.streamTypeByName.contains(intermediateStreamCall.name)
                  && lastStreamType != HandlerAssigner.streamTypeByName[intermediateStreamCall.name]) {
                lastStreamType = HandlerAssigner.streamTypeByName[intermediateStreamCall.name]!!
              }
            }
          }
          val valueToSet = event.virtualMachine().mirrorOf(lastStreamType)
          if (idx == 0) idx = -1
          arrayInstance.setValue(idx + 1, valueToSet)
          charArray[idx + 1] = lastStreamType
          arrayInstance.setValue(idx + 2, valueToSet)
          charArray[idx + 2] = '.'
          val listParam = mutableListOf(arrayInstance)
          targetClass.invokeMethod(event.thread(),
                                   targetClass.methodsByName("init")[0],
                                   listParam,
                                   0)
        }
      }
      val field = targetClass.fieldByName("consumersArray")
      val fieldValue = targetClass.getValues(listOf(field))[field]
      if (fieldValue is ArrayReference && index < fieldValue.values.size) {
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
      val castValue: Value
      if (charArray[index - 1] == 'i') {
        castValue = (targetClass as ClassType).invokeMethod(event.thread(),
                                                            targetClass.methodsByName("getIntConsumer")[0],
                                                            listOf(event.virtualMachine().mirrorOf(index - 1)),
                                                            0)
      }
      else { // todo long, double
        castValue = (targetClass as ClassType).invokeMethod(event.thread(),
                                                            targetClass.methodsByName("getConsumer")[0],
                                                            listOf(event.virtualMachine().mirrorOf(index - 1)),
                                                            0)
      }
      try {
        val newReturnValue = (valueToReturn as ObjectReference).invokeMethod(event.thread(),
                                                                             returnValue.referenceType().methodsByName("peek")[0],
                                                                             listOf(castValue),
                                                                             ClassType.INVOKE_SINGLE_THREADED)
        event.thread().forceEarlyReturn(newReturnValue)
        (stackFrame.descriptor.debugProcess as DebugProcessImpl).session.resume()
      }
      catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }
}
