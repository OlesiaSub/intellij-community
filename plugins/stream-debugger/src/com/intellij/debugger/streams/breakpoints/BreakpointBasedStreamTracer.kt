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
import com.jetbrains.jdi.ObjectReferenceImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import com.sun.jdi.IntegerValue
import com.sun.jdi.ReferenceType
import java.util.concurrent.atomic.AtomicBoolean

class BreakpointBasedStreamTracer(private val mySession: XDebugSession,
                                  private val chainReferences: MutableList<out PsiMethod>,
                                  private val chainFile: VirtualFile) : StreamTracer {

  override fun trace(chain: StreamChain, callback: TracingCallback) {

    traverseBreakpoints()
  }

  private fun traverseBreakpoints() {
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
    breakpointSetter.setBreakpoint(chainReferences[0].containingFile, chainReferences[0].textOffset)
    breakpointSetter.setRequest()
    mySession.debugProcess.resume(mySession.suspendContext)
    mySession.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        ApplicationManager.getApplication().invokeLater {
          if (!returnedToFile.get()) {
            if (mySession.currentPosition!!.file.name.equals(chainFile.name)) {
              val loadedClass = stackFrame.stackFrameProxy.virtualMachine.classesByName(className)[0]
              val peekArray = loadedClass!!.fieldByName("peekArray")
              val fieldValue = loadedClass.getValues(listOf(peekArray))[peekArray]
              val resultList = mutableListOf<MutableList<Int>>()
              var cnt = 0;
              if (fieldValue is ArrayReference) {
                for (map in fieldValue.values) {
                  if (map != null && map is ArrayReference) {
                    resultList.add(cnt, mutableListOf())
                    for (mapValue in map.values) {
                      if (mapValue != null) {
                        val value = (mapValue as ObjectReferenceImpl).getValue(mapValue.referenceType().fieldByName("value"))
                        if (value != null && value is IntegerValue) {
                          resultList.get(cnt).add(value.value())
                        }
                      }
                    }
                    cnt++
                  }
                }
              }
              resultList.forEach { l ->
                run {
                  println("\nNEXT")
                  l.forEach { println(it) }
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