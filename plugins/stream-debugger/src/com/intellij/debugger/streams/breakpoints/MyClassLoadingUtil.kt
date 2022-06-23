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
import com.intellij.debugger.streams.breakpoints.consumers.ClassesExtractor
import com.sun.jdi.*
import java.util.function.IntConsumer

class MyClassLoadingUtil(private val contextImpl: EvaluationContextImpl,
                         private val process: DebugProcessImpl,
                         private val stackFrame: JavaStackFrame) {

  val classLoader: ClassLoaderReference = ClassLoadingUtils.getClassLoader(contextImpl, process)
  val loadedClasses: MutableSet<String> = mutableSetOf()

  @Throws(InvocationException::class, ClassNotLoadedException::class, IncompatibleThreadStateException::class,
          InvalidTypeException::class, EvaluateException::class)
  fun loadClassByName(className: String, classNameToExtract: String): ReferenceType? {
    if (loadedClasses.contains(className)) {
      return null
    }
    loadedClasses.add(className)
    try {
      var classReference: ReferenceType? = null
      process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
                                                                         stackFrame.stackFrameProxy.threadProxy()) {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun threadAction(suspendContext: SuspendContextImpl) {
          val bytes = ClassesExtractor().extractConsumer(classNameToExtract)
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
}