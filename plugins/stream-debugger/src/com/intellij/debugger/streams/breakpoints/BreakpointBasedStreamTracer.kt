// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.TraceResultInterpreter
import com.intellij.debugger.streams.trace.TracingCallback
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiMethod
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import java.util.concurrent.atomic.AtomicBoolean


class BreakpointBasedStreamTracer(private val mySession: XDebugSession,
                                  private val chainReferences: MutableList<out PsiMethod>,
                                  private val chainFile: VirtualFile,
                                  private val myResultInterpreter: TraceResultInterpreter) : StreamTracer {

  override fun trace(chain: StreamChain, callback: TracingCallback) {
    val stackFrame = (mySession.currentStackFrame as JavaStackFrame)
    val breakpointSetter = BreakpointSetter(mySession.getProject(),
                                            (stackFrame.descriptor.debugProcess as DebugProcessImpl),
                                            stackFrame,
                                            chainReferences.size)
    val contextImpl = EvaluationContextImpl(mySession.suspendContext as SuspendContextImpl,
                                            stackFrame.stackFrameProxy)
    val classLoadingUtil = MyClassLoadingUtil(contextImpl,
                                              (stackFrame.descriptor.debugProcess as DebugProcessImpl),
                                              stackFrame)
    classLoadingUtil.loadClass()
    // todo add className to stream debugger bundle (another .properties file?)
    val className = "com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer"
    val returnedToFile = AtomicBoolean(false)
    breakpointSetter.setRequest()
    mySession.debugProcess.resume(mySession.suspendContext)
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
                  if (loadedClass is ClassType) {
                    val reference =
                      loadedClass.invokeMethod(
                        (mySession.currentStackFrame as JavaStackFrame).stackFrameProxy.threadProxy().threadReference,
                        loadedClass.methodsByName("getResult")[0],
                        listOf(),
                        0)
                    if (reference is ArrayReference) {
                      val interpretedResult = try {
                        myResultInterpreter.interpret(chain, reference)
                      }
                      catch (t: Throwable) {
                        throw t
                      }
                      val context = EvaluationContextImpl(mySession.suspendContext as SuspendContextImpl,
                                                          (mySession.currentStackFrame as JavaStackFrame).stackFrameProxy)
                      callback.evaluated(interpretedResult, context)
                      return
                    }
                  }
                }
              })
              returnedToFile.set(true)
              mySession.getDebugProcess().startStepOut(mySession.getSuspendContext())
            }
            else {
              mySession.debugProcess.resume(mySession.suspendContext)
            }
          }
        }
      }
    })
  }
}