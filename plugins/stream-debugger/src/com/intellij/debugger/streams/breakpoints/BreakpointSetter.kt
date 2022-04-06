// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.ui.breakpoints.BreakpointManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import javax.swing.SwingUtilities


class BreakpointSetter(private val project: Project) {
  fun setBreakpoint(currentFile: PsiFile, offset: Int) {
    val document = runReadAction { PsiDocumentManager.getInstance(project).getDocument(currentFile) } ?: return
    val runnable = {
      val lineIndex = document.getLineNumber(offset)
      val debuggerManager = DebuggerManagerEx.getInstance(project)
      val bpManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager()
      createMethodBreakpoint(bpManager, lineIndex, document)
    }

    if (!SwingUtilities.isEventDispatchThread()) {
      DebuggerInvocationUtil.invokeAndWait(project, runnable, ModalityState.defaultModalityState())
    }
    else {
      runnable.invoke()
    }
  }

  private fun createMethodBreakpoint(breakpointManager: BreakpointManager, lineIndex: Int, document: Document) {
    runWriteAction {
      val bp = breakpointManager.addMethodBreakpoint(document, lineIndex)
      bp?.xBreakpoint?.properties?.WATCH_EXIT = true
      bp?.xBreakpoint?.properties?.EMULATED = false
    }
  }

  //@Suppress("UNCHECKED_CAST")
  //private inline fun <reified T : XBreakpointType<*, *>> findBreakpointType(javaClass: Class<T>): XLineBreakpointType<XBreakpointProperties<*>> =
  //  XDebuggerUtil.getInstance().findBreakpointType(javaClass) as XLineBreakpointType<XBreakpointProperties<*>>

}
