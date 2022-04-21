// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.breakpoints.BreakpointManager
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.sun.jdi.ClassLoaderReference
import com.sun.jdi.VMDisconnectedException


class BreakpointSetter(private val project: Project,
                       private val process: DebugProcessImpl,
                       private val myStackFrame: JavaStackFrame,
                       private val contextImpl: EvaluationContextImpl) {

  var breakpoint: MethodBreakpoint? = null

  fun setRequest() {
    try {
      process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
                                                                         myStackFrame.stackFrameProxy.threadProxy()) {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun threadAction(suspendContext: SuspendContextImpl) {
          //ApplicationManager.getApplication().assertIsDispatchThread()
          val myBp = MyMethodBreakpoint(breakpoint!!.project, breakpoint!!.xBreakpoint, process, myStackFrame)
          val meReq = process.requestsManager.createMethodExitRequest(myBp)
          meReq.addClassFilter("java.util.stream.ReferencePipeline")
          //DebuggerUtilsAsync.setEnabled(meReq, true) // хз в чем разница
          meReq.enable()
        }
      })
    }
    catch (e: VMDisconnectedException) {
      println("Virtual Machine is disconnected.")
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun setBreakpoint(currentFile: PsiFile, offset: Int): MethodBreakpoint? {
    val document = runReadAction { PsiDocumentManager.getInstance(project).getDocument(currentFile) } ?: return null
    var bp: MethodBreakpoint? = null
    var bpManager: BreakpointManager?

    runWriteAction {
      val lineIndex = document.getLineNumber(offset)
      bpManager = DebuggerManagerEx.getInstanceEx(project).breakpointManager
      bp = bpManager!!.addMethodBreakpoint(document, lineIndex)
      bp?.xBreakpoint?.properties?.WATCH_EXIT = true
      bp?.xBreakpoint?.properties?.EMULATED = false
      if (bp != null) {
        if (breakpoint != null) {
          bpManager!!.removeBreakpoint(breakpoint)
        }
        breakpoint = bp
      }
    }
    return bp
  }

}
