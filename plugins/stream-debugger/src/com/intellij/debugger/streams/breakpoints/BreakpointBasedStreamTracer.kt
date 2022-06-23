// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.streams.breakpoints.consumers.handlers.BasicHandler
import com.intellij.debugger.streams.breakpoints.consumers.handlers.HandlerAssigner
import com.intellij.debugger.streams.trace.EvaluateExpressionTracer
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.TraceResultInterpreter
import com.intellij.debugger.streams.trace.TracingCallback
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.MessageType
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import com.sun.jdi.Value
import org.jetbrains.annotations.NotNull
import java.util.concurrent.atomic.AtomicBoolean

class BreakpointBasedStreamTracer(private val mySession: XDebugSession,
                                  private val myResultInterpreter: TraceResultInterpreter) : StreamTracer {

  private lateinit var streamChain: StreamChain

  override fun trace(@NotNull chain: StreamChain, @NotNull callback: TracingCallback) {

    EvaluateExpressionTracer.time = System.currentTimeMillis()
    streamChain = chain
    val stackFrame = (mySession.currentStackFrame as JavaStackFrame)
    val breakpointSetter = BreakpointSetter(mySession.project, stackFrame, chain, this)
    val contextImpl = EvaluationContextImpl(mySession.suspendContext as SuspendContextImpl, stackFrame.stackFrameProxy)
    val classLoadingUtil = MyClassLoadingUtil(contextImpl, (stackFrame.descriptor.debugProcess as DebugProcessImpl), stackFrame)
    loadOperationsClasses(classLoadingUtil)
    MyFilteredRequestor.terminationCallReached = false
    val returnedToFile = AtomicBoolean(false)
    breakpointSetter.setRequest()
    val runnable = {
      mySession.debugProcess.resume(mySession.suspendContext)
    }
    ApplicationManager.getApplication().invokeLater(runnable)
    var reference: Value? = null
    mySession.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        ApplicationManager.getApplication().invokeLater {
          if (!returnedToFile.get()) {
            if (MyFilteredRequestor.terminationCallReached) {
              MyFilteredRequestor.terminationCallReached = false
              val loadedClass = stackFrame.stackFrameProxy.virtualMachine.classesByName("com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer")[0]
              val debugProcess = stackFrame.descriptor.debugProcess as DebugProcessImpl
              debugProcess.managerThread.schedule(object : DebuggerContextCommandImpl(debugProcess.debuggerContext,
                                                                                      stackFrame.stackFrameProxy.threadProxy()) {
                override fun getPriority(): PrioritizedTask.Priority {
                  return PrioritizedTask.Priority.HIGH
                }

                override fun threadAction(suspendContext: SuspendContextImpl) {
                  getTraceResultsForStreamChain(mySession.currentStackFrame as JavaStackFrame)
                  if (loadedClass is ClassType) {
                    reference = loadedClass.invokeMethod(
                      (mySession.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference,
                      loadedClass.methodsByName("getResult")[0],
                      listOf(),
                      0)
                  }
                }
              })
              returnedToFile.set(true)
              mySession.debugProcess.startStepOut(mySession.suspendContext)
            }
            else {
              mySession.debugProcess.resume(mySession.suspendContext)
            }
          }
          else {
            if (reference == null) reference = MyFilteredRequestor.reference
            if (reference is ArrayReference && reference != null) {
              processParallelStreamCall()
              interpretTraceResult(reference as ArrayReference, callback)
            }
          }
        }
      }
    })
  }

  private fun processParallelStreamCall() {
    if (streamChain.intermediateCalls.any { it.name.equals("parallel") }) {
      XDebuggerManagerImpl.getNotificationGroup()
        .createNotification("Parallel stream was converted to sequential during stream chain evaluation", MessageType.INFO)
        .notify(mySession.project)
    }
  }

  private fun interpretTraceResult(@NotNull reference: ArrayReference, @NotNull callback: TracingCallback) {
    val interpretedResult = try {
      myResultInterpreter.interpret(streamChain, reference)
    }
    catch (t: Throwable) {
      // todo callback.evaluationFailed?
      throw t;
    }
    val context = EvaluationContextImpl(mySession.suspendContext as SuspendContextImpl,
                                        (mySession.currentStackFrame as JavaStackFrame).stackFrameProxy)
    callback.evaluated(interpretedResult, context)
  }

  private fun loadOperationsClasses(@NotNull classLoadingUtil: MyClassLoadingUtil) {
    // todo "names" -> vars
    classLoadingUtil.loadClassByName("com.intellij.debugger.streams.breakpoints.consumers.handlers.StreamOperationHandlerBase",
                                     "/com/intellij/debugger/streams/breakpoints/consumers/handlers/StreamOperationHandlerBase.class")
    classLoadingUtil.loadClassByName("com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.match.MatchHandler",
                                     "/com/intellij/debugger/streams/breakpoints/consumers/handlers/impl/terminal/match/MatchHandler.class") // todo зачем оно тут
    loadHandlerClass(classLoadingUtil, BasicHandler().toString())
    streamChain.intermediateCalls.forEach { streamCall ->
      run {
        if (HandlerAssigner.intermediateHandlersByName.containsKey(streamCall.name)) {
          loadHandlerClass(classLoadingUtil, HandlerAssigner.intermediateHandlersByName.get(streamCall.name).toString())
        }
        else {
          println("no such handler " + streamCall.name)
        }
      }
    }
    if (HandlerAssigner.terminalHandlersByName.containsKey(streamChain.terminationCall.name)) {
      loadHandlerClass(classLoadingUtil, HandlerAssigner.terminalHandlersByName.get(streamChain.terminationCall.name).toString())
    }
    else {
      println("no such terminal handler name " + streamChain.terminationCall.name)
    }
    classLoadingUtil.loadClassByName("com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer", "PeekConsumer.class")
  }

  private fun loadHandlerClass(@NotNull classLoadingUtil: MyClassLoadingUtil, handlerClassName: String) {
    var className = handlerClassName.replace('.', '/')
    className = "/" + className.substring(0, className.lastIndexOf('@'))
    val nClassName = className.replace('/', '.').substring(1, className.length)
    classLoadingUtil.loadClassByName(nClassName, "$className.class")
  }

  fun getTraceResultsForStreamChain(@NotNull stackFrame: JavaStackFrame) {
    var index = 0
    streamChain.intermediateCalls.forEachIndexed { currentIndex, streamCall ->
      run {
        index = currentIndex
        if (HandlerAssigner.intermediateHandlersByName.containsKey(streamCall.name)) {
          invokeOperationResultSetter(stackFrame, index, HandlerAssigner.intermediateHandlersByName.get(streamCall.name).toString())
        } else {
          invokeOperationResultSetter(stackFrame, index, BasicHandler().toString())
        }
      }
    }
    val streamCall = streamChain.terminationCall.name
    if (HandlerAssigner.terminalHandlersByName.containsKey(streamCall)) {
      invokeOperationResultSetter(stackFrame,
                                  if (streamChain.intermediateCalls.size == 0) 0 else index + 1,
                                  HandlerAssigner.terminalHandlersByName.get(streamCall).toString())
    } else {
      invokeOperationResultSetter(stackFrame,
                                  if (streamChain.intermediateCalls.size == 0) 0 else index + 1,
                                  "BasicHandler")
    }
  }

  private fun invokeOperationResultSetter(@NotNull stackFrame: JavaStackFrame, index: Int, handlerClassName: String) {
    val className = handlerClassName.substring(0, handlerClassName.lastIndexOf('@'))
    val loadedClass = stackFrame.stackFrameProxy.virtualMachine.classesByName(className)[0]
    val method = loadedClass!!.methodsByName("setOperationResult")[0]
    if (loadedClass is ClassType) {
      loadedClass.invokeMethod(
        stackFrame.stackFrameProxy.threadProxy().threadReference,
        method,
        listOf(stackFrame.stackFrameProxy.virtualMachine.mirrorOf(index + 1)),
        0)
    }
  }
}