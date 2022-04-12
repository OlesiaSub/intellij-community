// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.ui.breakpoints.BreakpointManager
import com.intellij.debugger.ui.breakpoints.MethodBreakpoint
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import javax.swing.SwingUtilities


class BreakpointSetter(private val project: Project) {

  val breakpoints: MutableList<MethodBreakpoint> = mutableListOf()

  fun setBreakpoint(currentFile: PsiFile, offset: Int): MethodBreakpoint? {
    val document = runReadAction { PsiDocumentManager.getInstance(project).getDocument(currentFile) } ?: return null
    var bp : MethodBreakpoint? = null
    val runnable = {
      val lineIndex = document.getLineNumber(offset)
      val bpManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager()
      bp = createMethodBreakpoint(bpManager, lineIndex, document)
      if (bp != null) {
        breakpoints.add(bp!!)
      }
    }

    if (!SwingUtilities.isEventDispatchThread()) {
      DebuggerInvocationUtil.invokeAndWait(project, runnable, ModalityState.defaultModalityState())
    }
    else {
      runnable.invoke()
    }

    return bp
  }

  private fun createMethodBreakpoint(breakpointManager: BreakpointManager, lineIndex: Int, document: Document) : MethodBreakpoint? {
    var bp : MethodBreakpoint? = null
    runWriteAction {
      bp = breakpointManager.addMethodBreakpoint(document, lineIndex)
      bp?.xBreakpoint?.properties?.WATCH_EXIT = true
      bp?.xBreakpoint?.properties?.EMULATED = false
    }
    return bp
  }

  //private inline fun <reified T : XBreakpointType<*, *>> findBreakpointType(javaClass: Class<T>): XLineBreakpointType<XBreakpointProperties<*>> =
  //  XDebuggerUtil.getInstance().findBreakpointType(javaClass) as XLineBreakpointType<XBreakpointProperties<*>>

}
