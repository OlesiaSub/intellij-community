// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
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
                          private val chain: StreamChain,
                          private val mySession: XDebugSession) : FilteredRequestorImpl(project) {

  private var methods: MutableSet<Method> = mutableSetOf()
  private val targetClassName = "com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer"

  companion object {
    var index = 0
    var chainMethodIndex = 0
    var initialized = false
    var terminationCallReached = false
  }

  lateinit var charArray: Array<Char>
  private var toReturn: Boolean = false
  lateinit var requests: MutableList<MethodExitRequest>

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
      requests.forEach { it.disable() }
      handleMethodExitEvent(event)
      requests.forEach { it.enable() }
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
    println("here " + event.method().name())
    val methodName = event.method().name()
    if (event.method().name().equals(
        chain.terminationCall.name) && event.method().arguments().size == chain.terminationCall.arguments.size) {
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
                 || methodName.equals("stream") || methodName.equals("intStream")
                 || methodName.equals("longStream") || methodName.equals("doubleStream"))) {
      if (initialized && ((methodName.equals("intStream") || methodName.equals("stream")
                           || methodName.equals("longStream") || methodName.equals("doubleStream")))) {
        return
      }
      if (!methodName.equals("stream") && !methodName.equals("intStream")
          && !methodName.equals("longStream") && !methodName.equals("doubleStream")) {
        chainMethodIndex++
      }
      //val runnableVal = runnable@{
      val targetClass = event.virtualMachine().classesByName(targetClassName)[0]
      if (!initialized) {
        initialized = true;
        if (targetClass is ClassType) {
          val chainSize = chain.intermediateCalls.size + 1
          targetClass.invokeMethod(event.thread(),
                                   targetClass.methodsByName("init")[0],
                                   listOf(event.virtualMachine().mirrorOf(chainSize)),
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
      var castValue: Value? = null
      println(event.method())
      val returnType = event.method().returnType().toString()
      if (returnType.contains("java.util.stream.Stream") || returnType.contains("one.util.streamex.AbstractStreamEx")) {
        castValue = (targetClass as ClassType).invokeMethod(event.thread(),
                                                            targetClass.methodsByName("getConsumer")[0],
                                                            listOf(event.virtualMachine().mirrorOf(index - 1)),
                                                            0)
      }
      else if (returnType.contains("java.util.stream.IntStream")) {
        castValue = (targetClass as ClassType).invokeMethod(event.thread(),
                                                            targetClass.methodsByName("getIntConsumer")[0],
                                                            listOf(event.virtualMachine().mirrorOf(index - 1)),
                                                            0)
      }
      else if (returnType.contains("java.util.stream.LongStream")) {
        castValue = (targetClass as ClassType).invokeMethod(event.thread(),
                                                            targetClass.methodsByName("getLongConsumer")[0],
                                                            listOf(event.virtualMachine().mirrorOf(index - 1)),
                                                            0)
      }
      else if (returnType.contains("java.util.stream.DoubleStream")) {
        castValue = (targetClass as ClassType).invokeMethod(event.thread(),
                                                            targetClass.methodsByName("getDoubleConsumer")[0],
                                                            listOf(event.virtualMachine().mirrorOf(index - 1)),
                                                            0)
      }
      println(castValue)
      if (event.method().name().contains("sortedBy")) {
        println("here")
        //(stackFrame.descriptor.debugProcess as DebugProcessImpl).managerThread.schedule(object : DebuggerContextCommandImpl(
        //  (stackFrame.descriptor.debugProcess as DebugProcessImpl).debuggerContext, stackFrame.stackFrameProxy.threadProxy()) {
        //  override fun threadAction(suspendContext: SuspendContextImpl) {
        val classLoader = (event.method().returnType() as ReferenceType).classLoader()
        //(stackFrame.descriptor.debugProcess as DebugProcessImpl).loadClass(evaluationContext, "java.util.function.Consumer", classLoader)
        val m = classLoader.referenceType().methodsByName("loadClass").get(1)
        val classReference: Value = classLoader.invokeMethod(event.thread(), m,
                                                             listOf(event.virtualMachine().mirrorOf("java.util.function.Consumer")), 0)
        (stackFrame.descriptor.debugProcess as DebugProcessImpl).setVisible(classReference, classLoader)
        println("IN METHOD^^^ " + event.method().name())
        val newReturnValue = (valueToReturn as ObjectReference)
          .invokeMethod(event.thread(),
                        returnValue.referenceType().methodsByName("peek")[0],
                        listOf(castValue),
                        0)

        event.thread().forceEarlyReturn(newReturnValue)
        //  }
        //})
      }
      try {
        val newReturnValue = (valueToReturn as ObjectReference)
          .invokeMethod(event.thread(),
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