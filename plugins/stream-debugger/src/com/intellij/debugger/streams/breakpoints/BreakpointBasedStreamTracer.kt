// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.streams.breakpoints.consumers.handlers.HandlerAssigner
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.TraceResultInterpreter
import com.intellij.debugger.streams.trace.TracingCallback
import com.intellij.debugger.streams.wrapper.StreamCall
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiMethod
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import com.sun.jdi.Value
import java.util.concurrent.atomic.AtomicBoolean

class BreakpointBasedStreamTracer(private val mySession: XDebugSession,
                                  private val chainReferences: MutableList<out PsiMethod>,
                                  private val myResultInterpreter: TraceResultInterpreter) : StreamTracer {

  override fun trace(chain: StreamChain, callback: TracingCallback) {
    val stackFrame = (mySession.currentStackFrame as JavaStackFrame)
    val breakpointSetter = BreakpointSetter(mySession.getProject(),
                                            (stackFrame.descriptor.debugProcess as DebugProcessImpl),
                                            stackFrame,
                                            chain)
    val contextImpl = EvaluationContextImpl(mySession.suspendContext as SuspendContextImpl,
                                            stackFrame.stackFrameProxy)
    val classLoadingUtil = MyClassLoadingUtil(contextImpl,
                                              (stackFrame.descriptor.debugProcess as DebugProcessImpl),
                                              stackFrame)
    classLoadingUtil.loadClassByName("com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer", "PeekConsumer.class")
    classLoadingUtil.loadClassByName("com.intellij.debugger.streams.breakpoints.consumers.handlers.StreamOperationHandlerBase",
                                     "/com/intellij/debugger/streams/breakpoints/consumers/handlers/StreamOperationHandlerBase.class")
    classLoadingUtil.loadClassByName("com.intellij.debugger.streams.breakpoints.consumers.handlers.BasicHandler",
                                     "/com/intellij/debugger/streams/breakpoints/consumers/handlers/BasicHandler.class")
    classLoadingUtil.loadClassByName("com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.AnyMatchHandler",
                                     "/com/intellij/debugger/streams/breakpoints/consumers/handlers/impl/terminal/AnyMatchHandler.class")

    //loadOperationsClasses(chain, classLoadingUtil, stackFrame)
    MyFilteredRequestor.terminationCallReached = false
    MyFilteredRequestor.initialized = false
    MyFilteredRequestor.index = 0
    // todo add className to stream debugger bundle (another .properties file?)
    val className = "com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer"
    val returnedToFile = AtomicBoolean(false)
    breakpointSetter.setRequest()
    mySession.debugProcess.resume(mySession.suspendContext)
    var reference: Value? = null
    mySession.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        ApplicationManager.getApplication().invokeLater {
          if (!returnedToFile.get()) {
            if (MyFilteredRequestor.terminationCallReached) {
              val loadedClass = stackFrame.stackFrameProxy.virtualMachine.classesByName(className)[0]
              val debugProcess = stackFrame.descriptor.debugProcess as DebugProcessImpl
              debugProcess.managerThread.schedule(object : DebuggerContextCommandImpl(debugProcess.debuggerContext,
                                                                                      stackFrame.stackFrameProxy.threadProxy()) {
                override fun getPriority(): PrioritizedTask.Priority {
                  return PrioritizedTask.Priority.HIGH
                }

                override fun threadAction(suspendContext: SuspendContextImpl) {
                  getTraceResultsForStreamChain(chain, (mySession.currentStackFrame as JavaStackFrame))
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
            if (reference is ArrayReference && reference != null) {
              interpretTraceResult(reference as ArrayReference, chain, callback)
            }
          }
        }
      }
    })
  }

  private fun interpretTraceResult(reference: ArrayReference, chain: StreamChain, callback: TracingCallback) {
    val interpretedResult = try {
      myResultInterpreter.interpret(chain, reference)
    }
    catch (t: Throwable) {
      throw t
    }
    val context = EvaluationContextImpl(mySession.suspendContext as SuspendContextImpl,
                                        (mySession.currentStackFrame as JavaStackFrame).stackFrameProxy)
    callback.evaluated(interpretedResult, context)
  }

  private fun loadOperationsClasses(chain: StreamChain, classLoadingUtil: MyClassLoadingUtil, stackFrame: JavaStackFrame) {
    chain.intermediateCalls.forEach { streamCall ->
      run {
        var className = HandlerAssigner.getHandlerByName(streamCall.name).toString().replace('.', '/')
        className = "/" + className.substring(0, className.lastIndexOf('@'))
        val nClassName = className.replace('/', '.').substring(1, className.length)
        classLoadingUtil.loadClassByName(nClassName, "$className.class")
      }
    }
  }

  private fun getTraceResultsForStreamChain(chain: StreamChain, stackFrame: JavaStackFrame) {
    var index = 0
    chain.intermediateCalls.forEachIndexed { currentIndex, streamCall ->
      run {
        index = currentIndex
        invokeOperationResultSetter(streamCall, stackFrame, index)
      }
    }
    val streamCall = chain.terminationCall
    invokeOperationResultSetter(streamCall, stackFrame, index + 1)
  }

  private fun invokeOperationResultSetter(streamCall: StreamCall, stackFrame: JavaStackFrame, index: Int) {
    var className = HandlerAssigner.getHandlerByName(streamCall.name).toString()
    className = className.substring(0, className.lastIndexOf('@'))
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