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
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.request.EventRequest


class BreakpointSetter(private val project: Project,
                       private val stackFrame: JavaStackFrame,
                       private val chain: StreamChain,
                       private val breakpointBasedStreamTracer: BreakpointBasedStreamTracer) {

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
  private val process = stackFrame.descriptor.debugProcess as DebugProcessImpl

  fun setRequest() {
    try {
      process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
                                                                         stackFrame.stackFrameProxy.threadProxy()) {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun threadAction(suspendContext: SuspendContextImpl) {
          //ApplicationManager.getApplication().assertIsDispatchThread()
          val myFilteredRequestor = MyFilteredRequestor(project, stackFrame, chain, breakpointBasedStreamTracer)
          myFilteredRequestor.SUSPEND_POLICY = DebuggerSettings.SUSPEND_THREAD
          val requests: MutableList<EventRequest> = mutableListOf()
          classFilters.forEach {
            val methodExitRequest = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
            methodExitRequest.addClassFilter(it)
            requests.add(methodExitRequest)
            methodExitRequest.enable()
          }
          val d = process.requestsManager.createExceptionRequest(myFilteredRequestor, null, true, true)
          d.enable()
          myFilteredRequestor.requests = requests
          myFilteredRequestor.requests.add(d)
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