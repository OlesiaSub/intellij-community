// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.project.Project
import com.sun.jdi.*
import com.sun.jdi.event.ExceptionEvent
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.request.EventRequest

class MyFilteredRequestor(project: Project,
                          private val stackFrame: JavaStackFrame,
                          private val streamChain: StreamChain,
                          private val breakpointBasedStreamTracer: BreakpointBasedStreamTracer) : FilteredRequestorImpl(project) {

  private var methods: MutableSet<Method> = mutableSetOf()
  private val targetClassName = "com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer"
  private val debugProcessImpl = stackFrame.descriptor.debugProcess as DebugProcessImpl

  companion object {
    var terminationCallReached = false
    var reference: Value? = null
  }

  lateinit var requests: MutableList<EventRequest>
  private var toReturn: Boolean = false
  private var index = 0
  private var chainMethodIndex = 0
  private var initialized = false
  private var streamExInitialized = false

  @Override
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent): Boolean {
    if (toReturn) {
      debugProcessImpl.requestsManager.deleteRequest(this)
      return false
    }
    val contextImpl = EvaluationContextImpl(action.suspendContext as SuspendContextImpl,
                                            (action.suspendContext as SuspendContextImpl).frameProxy)
    contextImpl.isAutoLoadClasses = true
    if (event is ExceptionEvent) {
      handleExceptionEvent(event, contextImpl)
      return true
    }
    if (event is MethodExitEvent) {
      if (methods.contains(event.method())) {
        methods.remove(event.method())
        return toReturn
      }
      methods.add(event.method())
      requests.forEach { it.disable() }
      handleMethodExitEvent(event, contextImpl)
      requests.forEach { it.enable() }
    }
    return toReturn
  }

  private fun handleMethodExitEvent(event: MethodExitEvent, contextImpl: EvaluationContextImpl) {
    println(event.method().name())
    val returnValue = event.returnValue()
    val methodName = event.method().name()
    if (methodName.equals(streamChain.terminationCall.name) && event.method().arguments().size == streamChain.terminationCall.arguments.size) {
      initializeResultTypes(event, returnValue, contextImpl)
      requests.forEach { it.disable() }
      toReturn = true
    }
    else if (event.method().name().equals(streamChain.terminationCall.name)) {
      return
    }
    else if (returnValue is ObjectReference
             && ((chainMethodIndex < streamChain.intermediateCalls.size && methodName.equals(streamChain.intermediateCalls.get(chainMethodIndex).name))
                 || (chainMethodIndex == streamChain.intermediateCalls.size && methodName.equals(streamChain.terminationCall.name))
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
        initializePeekConsumer(targetClass, contextImpl, event)
      }
      val consumersArrayField = targetClass.fieldByName("consumersArray")
      val consumersArrayValue = targetClass.getValues(listOf(consumersArrayField))[consumersArrayField]
      if (consumersArrayValue is ArrayReference && index < consumersArrayValue.values.size) {
        index++
      }
      var valueToReturn = returnValue
      if (event.method().name().equals("parallel")) {
        valueToReturn = invokeSequential(returnValue, contextImpl)
      }
      val consumer = getCorrespondingConsumer(event, targetClass as ClassType, contextImpl)
      try {
        val newReturnValue = debugProcessImpl.invokeInstanceMethod(contextImpl,
                                                                   (valueToReturn as ObjectReference),
                                                                   returnValue.referenceType().methodsByName("peek")[0],
                                                                   listOf(consumer),
                                                                   ClassType.INVOKE_SINGLE_THREADED,
                                                                   true)
        event.thread().forceEarlyReturn(newReturnValue)
        (debugProcessImpl).session.resume()
      }
      catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private fun initializePeekConsumer(targetClass: ReferenceType, contextImpl: EvaluationContextImpl, event: MethodExitEvent) {
    initialized = true
    if (targetClass is ClassType) {
      debugProcessImpl.invokeMethod(contextImpl,
                                    targetClass,
                                    targetClass.methodsByName("init")[0],
                                    listOf(event.virtualMachine().mirrorOf(streamChain.intermediateCalls.size + 1)),
                                    ClassType.INVOKE_SINGLE_THREADED,
                                    true)
    }
  }

  private fun handleExceptionEvent(event: ExceptionEvent, contextImpl: EvaluationContextImpl) {
    initializeResultTypes(event, event.exception(), contextImpl)
    requests.forEach { it.disable() }
    toReturn = true
    val loadedClass = stackFrame.stackFrameProxy.virtualMachine.classesByName("com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer")[0]
    breakpointBasedStreamTracer.getTraceResultsForStreamChain(stackFrame)
    if (loadedClass is ClassType) {
      reference = loadedClass.invokeMethod(
        stackFrame.stackFrameProxy.threadProxy().threadReference,
        loadedClass.methodsByName("getResult")[0],
        listOf(),
        0)
    }
    debugProcessImpl.requestsManager.deleteRequest(this)
  }

  private fun invokeSequential(returnValue: ObjectReference, contextImpl: EvaluationContextImpl) =
    debugProcessImpl.invokeInstanceMethod(contextImpl,
                                          returnValue,
                                          returnValue.referenceType().methodsByName("peek")[0],
                                          listOf(),
                                          ClassType.INVOKE_SINGLE_THREADED,
                                          true)


  private fun getParametersList(returnValue: Value?, vm: VirtualMachine): MutableList<ArrayReference?> {
    val classes: List<ReferenceType>
    if (returnValue!!.type().signature().equals("Ljava/lang/RuntimeException;"))  {
      classes = vm.classesByName("java.lang.Throwable[]")
      val arrayType = classes[0] as ArrayType
      val arrayInstance = arrayType.newInstance(1)
      arrayInstance.setValue(0, returnValue)
      return mutableListOf(arrayInstance)
    }
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
        classes = mutableListOf()
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

  private fun initializeResultTypes(event: LocatableEvent, returnValue: Value?, contextImpl: EvaluationContextImpl) {
    terminationCallReached = true
    index++
    val vm = event.virtualMachine()
    val targetClass = vm.classesByName(targetClassName)[0]
    if (targetClass is ClassType) {
      debugProcessImpl.invokeMethod(contextImpl,
                                    targetClass,
                                    targetClass.methodsByName("setReturnValue")[0],
                                    getParametersList(returnValue, vm),
                                    ClassType.INVOKE_SINGLE_THREADED,
                                    true)
    }
  }

  private fun getCorrespondingConsumer(event: MethodExitEvent, targetClass: ClassType, contextImpl: EvaluationContextImpl): Value {
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
    return debugProcessImpl.invokeMethod(contextImpl,
                                         targetClass,
                                         targetClass.methodsByName(methodToInvoke)[0],
                                         listOf(event.virtualMachine().mirrorOf(index - 1)),
                                         ClassType.INVOKE_SINGLE_THREADED,
                                         true)
  }
}