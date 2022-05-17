// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.VMDisconnectedException


class BreakpointSetter(private val project: Project,
                       private val process: DebugProcessImpl,
                       private val myStackFrame: JavaStackFrame,
                       private val chain: StreamChain,
                       private val mySession: XDebugSession) {

  fun setRequest() {
    try {
      process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
                                                                         myStackFrame.stackFrameProxy.threadProxy()) {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun threadAction(suspendContext: SuspendContextImpl) {
          //ApplicationManager.getApplication().assertIsDispatchThread()
          val myFilteredRequestor = MyFilteredRequestor(project, myStackFrame, chain, mySession, process)
          myFilteredRequestor.SUSPEND_POLICY = DebuggerSettings.SUSPEND_THREAD
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
}
