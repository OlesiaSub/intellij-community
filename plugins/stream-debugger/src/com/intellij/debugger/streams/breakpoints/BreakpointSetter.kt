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
          val myFilteredRequestor = MyFilteredRequestor(project, myStackFrame, chain, mySession)
          val methodExitRequest1 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest2 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest3 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest4 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest5 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest6 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest7 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest8 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest9 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest10 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest11 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          val methodExitRequest12 = process.requestsManager.createMethodExitRequest(myFilteredRequestor)
          methodExitRequest1.addClassFilter("java.util.stream.ReferencePipeline")
          methodExitRequest2.addClassFilter("java.util.stream.StreamSupport")
          methodExitRequest3.addClassFilter("java.util.stream.AbstractPipeline")
          methodExitRequest4.addClassFilter("java.util.stream.IntPipeline")
          methodExitRequest5.addClassFilter("java.util.stream.LongPipeline")
          methodExitRequest6.addClassFilter("java.util.stream.DoublePipeline")
          methodExitRequest7.addClassFilter("one.util.streamex.EntryStream")
          methodExitRequest8.addClassFilter("one.util.streamex.StreamEx")
          methodExitRequest9.addClassFilter("one.util.streamex.AbstractStreamEx")
          methodExitRequest10.addClassFilter("one.util.streamex.IntStreamEx")
          methodExitRequest11.addClassFilter("one.util.streamex.LongStreamEx")
          methodExitRequest12.addClassFilter("one.util.streamex.DoubleStreamEx")
          methodExitRequest1.enable()
          methodExitRequest2.enable()
          methodExitRequest3.enable()
          methodExitRequest4.enable()
          methodExitRequest5.enable()
          methodExitRequest6.enable()
          methodExitRequest7.enable()
          methodExitRequest8.enable()
          methodExitRequest9.enable()
          methodExitRequest10.enable()
          methodExitRequest11.enable()
          methodExitRequest12.enable()
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
