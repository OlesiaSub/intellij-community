// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.TracingCallback
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiMethod
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import java.util.concurrent.atomic.AtomicInteger

class BreakpointBasedStreamTracer(private val mySession: XDebugSession, private val chainReferences: List<PsiMethod>) : StreamTracer {

  override fun trace(chain: StreamChain, callback: TracingCallback) {
    traverseBreakpoints()
  }

  private fun traverseBreakpoints() {
    val stackFrame = (mySession.getCurrentStackFrame() as JavaStackFrame)
    val cimpl = EvaluationContextImpl(mySession.suspendContext as SuspendContextImpl,
                                      (mySession.currentStackFrame as JavaStackFrame).stackFrameProxy)
    val bs = BreakpointSetter(mySession.getProject(),
                              (stackFrame.descriptor.debugProcess as DebugProcessImpl),
                              (mySession.getCurrentStackFrame() as JavaStackFrame), cimpl)
    // setting all bps to actual stream methods
    //for (int i = 0; i < chainReferences.toArray().length; i++) {
    //  int offset = chainReferences.get(i).getTextOffset();
    //  MethodBreakpoint bp = bs.setBreakpoint(chainReferences.get(i).getContainingFile(), offset);
    //}
    val i = AtomicInteger(0)
    val offset = AtomicInteger(chainReferences[i.get()].textOffset)
    bs.setBreakpoint(chainReferences[i.get()].containingFile, offset.get())
    i.incrementAndGet()
    bs.setRequest()
    mySession.getDebugProcess().resume(mySession.getSuspendContext())
    mySession.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        ApplicationManager.getApplication().invokeLater {
          if (i.get() >= chainReferences.size) {
            mySession.getDebugProcess().resume(mySession.getSuspendContext())
            return@invokeLater
          }
          //bs.setRequest();
          offset.set(chainReferences[i.get()].textOffset)
          //bs.setBreakpoint(chainReferences.get(i.get()).getContainingFile(), offset.get());
          i.incrementAndGet()
          //mySession.getDebugProcess().resume(mySession.getSuspendContext());
          mySession.getDebugProcess().startStepOut(mySession.getSuspendContext())
        }
      }
    })
  }
}