// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.breakpoints.BreakpointManager
import com.intellij.debugger.ui.breakpoints.LineBreakpoint
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.sun.jdi.VMDisconnectedException


class BreakpointSetter(private val project: Project,
                       private val process: DebugProcessImpl,
                       private val myStackFrame: JavaStackFrame,
                       private val chainsSize: Int) {

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
          val myFilteredRequestor = MyFilteredRequestor(project, process, myStackFrame, chainsSize)
          val methodExitRequest1 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest2 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          methodExitRequest1.addClassFilter("java.util.stream.ReferencePipeline")
          methodExitRequest2.addClassFilter("java.util.stream.StreamSupport")
          methodExitRequest1.enable()
          methodExitRequest2.enable()
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

  // unused
  fun setBreakpoint(currentFile: PsiFile, offset: Int, project: Project): MethodBreakpoint? {
    val document = runReadAction { PsiDocumentManager.getInstance(project).getDocument(currentFile) } ?: return null
    //var methodBreakpoint: LineBreakpoint? = null
    var breakpointManager: BreakpointManager?

    runWriteAction {
      val lineIndex = document.getLineNumber(offset)
      breakpointManager = DebuggerManagerEx.getInstanceEx(project).breakpointManager
      val methodBreakpoint = breakpointManager!!.addLineBreakpoint(document, lineIndex)
      //methodBreakpoint?.xBreakpoint?.properties?.WATCH_EXIT = true
      //methodBreakpoint?.xBreakpoint?.properties?.EMULATED = false
      //if (methodBreakpoint != null) {
      //  if (breakpoint != null) {
      //    breakpointManager!!.removeBreakpoint(breakpoint)
      //  }
      //  breakpoint = methodBreakpoint
      //}
    }
    println("SET")
    return null
  }

}
