// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.streams.breakpoints.consumers.ConsumerExtractor
import com.sun.jdi.*

class MyClassLoadingUtil(private val contextImpl: EvaluationContextImpl,
                         private val process: DebugProcessImpl,
                         private val stackFrame: JavaStackFrame) {

  private val className = "com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer"

  @Throws(InvocationException::class, ClassNotLoadedException::class, IncompatibleThreadStateException::class,
          InvalidTypeException::class, EvaluateException::class)
  fun loadConsumerClass(): ReferenceType? {
    try {
      var classReference: ReferenceType? = null
      process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
                                                                         stackFrame.stackFrameProxy.threadProxy()) {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun threadAction(suspendContext: SuspendContextImpl) {
          val classLoader = ClassLoadingUtils.getClassLoader(contextImpl, process)
          val bytes = ConsumerExtractor().extractConsumer()
          ClassLoadingUtils.defineClass(className, bytes, contextImpl, process, classLoader)
          val debugProcess = contextImpl.debugProcess
          classReference = debugProcess.loadClass(contextImpl, className, classLoader)
        }
      })
      return classReference
    }
    catch (e: VMDisconnectedException) {
      println("Virtual Machine is disconnected.")
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
    return null
  }

  fun loadUtilClasses() {
    process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
                                                                       stackFrame.stackFrameProxy.threadProxy()) {
      override fun getPriority(): PrioritizedTask.Priority {
        return PrioritizedTask.Priority.HIGH
      }

      override fun threadAction(suspendContext: SuspendContextImpl) {
        val classLoader = ClassLoadingUtils.getClassLoader(contextImpl, process)
        val debugProcess = contextImpl.debugProcess
        debugProcess.loadClass(contextImpl, "java.lang.String", classLoader)
      }
    })
  }
}