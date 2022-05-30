// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.request.MethodExitRequest


class BreakpointSetter(private val project: Project,
                       private val process: DebugProcessImpl,
                       private val myStackFrame: JavaStackFrame,
                       private val chain: StreamChain,
                       private val mySession: XDebugSession) {

  private val classFilters = listOf("java.util.stream.ReferencePipeline",
                                    "java.util.stream.StreamSupport",
                                    "java.util.stream.AbstractPipeline",
                                    "java.util.stream.IntPipeline",
                                    "java.util.stream.LongPipeline",
                                    "java.util.stream.DoublePipeline",
                                    "one.util.streamex.EntryStream",
                                    "one.util.streamex.StreamEx",
                                    "one.util.streamex.AbstractStreamEx",
                                    "one.util.streamex.IntStreamEx",
                                    "one.util.streamex.LongStreamEx",
                                    "one.util.streamex.DoubleStreamEx")

  fun setRequest() {
    try {
      process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
                                                                         myStackFrame.stackFrameProxy.threadProxy()) {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun threadAction(suspendContext: SuspendContextImpl) {
          val myFilteredRequestor = MyFilteredRequestor(project, myStackFrame, chain, mySession)
          //ApplicationManager.getApplication().assertIsDispatchThread()
          val requests: MutableList<MethodExitRequest> = mutableListOf()
          classFilters.forEach {
            val methodExitRequest = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
            methodExitRequest.addClassFilter(it)
            requests.add(methodExitRequest)
            methodExitRequest.enable()
          }
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
