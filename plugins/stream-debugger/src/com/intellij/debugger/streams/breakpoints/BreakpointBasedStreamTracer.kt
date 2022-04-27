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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiMethod
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.jetbrains.jdi.FieldImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.ReferenceType
import java.util.concurrent.atomic.AtomicBoolean

class BreakpointBasedStreamTracer(private val mySession: XDebugSession,
                                  private val chainReferences: MutableList<out PsiMethod>,
                                  private val chainFile: VirtualFile) : StreamTracer {

  override fun trace(chain: StreamChain, callback: TracingCallback) {

    traverseBreakpoints()
  }

  private fun traverseBreakpoints() {
    val stackFrame = (mySession.getCurrentStackFrame() as JavaStackFrame)
    val breakpointSetter = BreakpointSetter(mySession.getProject(),
                                            (stackFrame.descriptor.debugProcess as DebugProcessImpl),
                                            (mySession.getCurrentStackFrame() as JavaStackFrame))
    val contextImpl = EvaluationContextImpl(mySession.suspendContext as SuspendContextImpl,
                                            (mySession.currentStackFrame as JavaStackFrame).stackFrameProxy)
    val classLoadingUtil = MyClassLoadingUtil(contextImpl,
                                              (stackFrame.descriptor.debugProcess as DebugProcessImpl),
                                              (mySession.getCurrentStackFrame() as JavaStackFrame))
    classLoadingUtil.loadClass()

    val returnedToFile = AtomicBoolean(false)
    breakpointSetter.setBreakpoint(chainReferences[0].containingFile, chainReferences[0].textOffset)
    breakpointSetter.setRequest()
    mySession.debugProcess.resume(mySession.suspendContext)
    mySession.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        ApplicationManager.getApplication().invokeLater {
          if (!returnedToFile.get()) {
            if (mySession.currentPosition!!.file.name.equals(chainFile.name)) {
              val loadedClass = (mySession.currentStackFrame as JavaStackFrame)
                .stackFrameProxy.virtualMachine.classesByName("com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer")[0]
              val peekArray = loadedClass.fieldByName("peekArray")
              val fieldValue = loadedClass.getValues(listOf(peekArray))[peekArray]
              if (fieldValue is ArrayReference) {
                for (map in fieldValue.values) {
                  if (map is ArrayReference) {
                    for (mapValue in map.values) {
                      println("here")
                    }
                  }
                }
              }
              returnedToFile.set(true)
            }
            else {
              mySession.debugProcess.resume(mySession.suspendContext)
              //mySession.getDebugProcess().startStepOut(mySession.getSuspendContext())
            }
          }
        }
      }
    })
  }
}