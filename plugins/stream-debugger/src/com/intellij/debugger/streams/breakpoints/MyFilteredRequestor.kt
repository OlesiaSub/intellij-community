// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.project.Project
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
    var terminationCallReached = false
  }

  lateinit var requests: MutableList<MethodExitRequest>
  private var toReturn: Boolean = false
  private var index = 0
  private var chainMethodIndex = 0
  private var initialized = false
  private var streamExInitialized = false

  @Override
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent): Boolean {
    if (toReturn) {
      return false
    }
    if (event is MethodExitEvent) {
      if (methods.contains(event.method())) { // костыльно
        methods.remove(event.method())
        return toReturn
      }
      methods.add(event.method())
      requests.forEach { it.disable() }
      handleMethodExitEvent(event)
      requests.forEach { it.enable() }
    }
    return toReturn
  }

  private fun handleMethodExitEvent(event: MethodExitEvent) {
    println(event.method().name())
    val returnValue = event.returnValue()
    val methodName = event.method().name()
    if (methodName.equals(chain.terminationCall.name) && event.method().arguments().size == chain.terminationCall.arguments.size) {
      initializeResultTypes(event, returnValue)
      requests.forEach { it.disable() }
      toReturn = true
    }
    else if (event.method().name().equals(chain.terminationCall.name)) {
      return
    }
    else if (returnValue is ObjectReference
             && ((chainMethodIndex < chain.intermediateCalls.size && methodName.equals(chain.intermediateCalls.get(chainMethodIndex).name))
                 || (chainMethodIndex == chain.intermediateCalls.size && methodName.equals(chain.terminationCall.name))
                 || methodName.equals("stream") || methodName.equals("of") || methodName.equals("intStream")
                 || methodName.equals("longStream") || methodName.equals("doubleStream"))) {
      if (initialized && ((methodName.equals("stream") || methodName.equals("of") || methodName.equals("intStream")
                           || methodName.equals("longStream") || methodName.equals("doubleStream")))) {
        return
      }
      if (!methodName.equals("stream") && !methodName.equals("intStream")
          && !methodName.equals("longStream") && !methodName.equals("doubleStream") && !methodName.equals("of")) {
        chainMethodIndex++
      }
      val targetClass = event.virtualMachine().classesByName(targetClassName)[0]
      if (!initialized) {
        initialized = true;
        if (targetClass is ClassType) {
          targetClass.invokeMethod(event.thread(),
                                   targetClass.methodsByName("init")[0],
                                   listOf(event.virtualMachine().mirrorOf(chain.intermediateCalls.size + 1)),
                                   0)
        }
      }
      val consumersArrayField = targetClass.fieldByName("consumersArray")
      val consumersArrayValue = targetClass.getValues(listOf(consumersArrayField))[consumersArrayField]
      if (consumersArrayValue is ArrayReference && index < consumersArrayValue.values.size) {
        index++
      }
      var valueToReturn = returnValue
      if (event.method().name().equals("parallel")) {
        valueToReturn = invokeSequential(returnValue, event)
      }
      val consumer = getCorrespondingConsumer(event, targetClass as ClassType)
      loadStreamExClasses(event)
      try {
        val newReturnValue = (valueToReturn as ObjectReference)
          .invokeMethod(event.thread(),
                        returnValue.referenceType().methodsByName("peek")[0],
                        listOf(consumer),
                        ClassType.INVOKE_SINGLE_THREADED)
        event.thread().forceEarlyReturn(newReturnValue)
        (stackFrame.descriptor.debugProcess as DebugProcessImpl).session.resume()
      }
      catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private fun invokeSequential(returnValue: ObjectReference, event: MethodExitEvent) =
    returnValue.invokeMethod(event.thread(),
                             returnValue.referenceType().methodsByName("sequential")[0],
                             listOf(),
                             0)

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
                               ClassType.INVOKE_SINGLE_THREADED)
    }
  }

  private fun getCorrespondingConsumer(event: MethodExitEvent, targetClass: ClassType): Value {
    val returnType = event.method().returnType().toString()
    var methodToInvoke = "getConsumer"
    if (returnType.contains("java.util.stream.IntStream") || returnType.contains("one.util.streamex.IntStreamEx")) {
      methodToInvoke = "getIntConsumer"
    }
    else if (returnType.contains("java.util.stream.LongStream") || returnType.contains("one.util.streamex.LongStreamEx")) {
      methodToInvoke = "getLongConsumer"
    }
    else if (returnType.contains("java.util.stream.DoubleStream") || returnType.contains("one.util.streamex.DoubleStreamEx")) {
      methodToInvoke = "getDoubleConsumer"
    }
    return targetClass.invokeMethod(event.thread(),
                                    targetClass.methodsByName(methodToInvoke)[0],
                                    listOf(event.virtualMachine().mirrorOf(index - 1)),
                                    ClassType.INVOKE_SINGLE_THREADED)
  }

  private fun loadStreamExClasses(event: MethodExitEvent) {
    if ((event.method().returnType().toString().contains("StreamEx")
         || event.method().returnType().toString().contains("EntryStream")) && !streamExInitialized) {
      streamExInitialized = true
      val classLoader = (event.method().returnType() as ReferenceType).classLoader()
      val loadClassMethod = classLoader.referenceType().methodsByName("loadClass").get(1)
      val consumers = listOf("Consumer", "IntConsumer", "LongConsumer", "DoubleConsumer")
      consumers.forEach {
        val classReference: Value = classLoader.invokeMethod(event.thread(),
                                                             loadClassMethod,
                                                             listOf(event.virtualMachine().mirrorOf("java.util.function.$it")),
                                                             ClassType.INVOKE_SINGLE_THREADED)
        (stackFrame.descriptor.debugProcess as DebugProcessImpl).setVisible(classReference, classLoader)
      }
    }
  }
}