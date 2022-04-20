// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.CommonClassNames
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.jdi.ClassLoaderReferenceImpl
import com.jetbrains.jdi.MethodImpl
import com.sun.jdi.*
import com.sun.jdi.event.MethodExitEvent

class MyClassLoadingUtil(private val contextImpl: EvaluationContextImpl,
                         private val process: DebugProcessImpl,
                         private val stackFrame: JavaStackFrame) {

  @Throws(InvocationException::class, ClassNotLoadedException::class, IncompatibleThreadStateException::class,
          InvalidTypeException::class, EvaluateException::class)
  fun loadClass(qName: String, event: MethodExitEvent): ClassLoaderReference? {
    var qName = qName
    //DebuggerManagerThreadImpl.assertIsManagerThread()
    qName = reformatArrayName(qName)
    val virtualMachine = event.virtualMachine()
    val classClassType = ContainerUtil.getFirstItem(virtualMachine.classesByName(CommonClassNames.JAVA_LANG_CLASS)) as ClassType
    var forNameMethod: Method?
    val args: MutableList<Value> = ArrayList()
    var refType: ReferenceType?
    var classLoader: ClassLoaderReference? = null

    try {
      process.managerThread.schedule(object : DebuggerContextCommandImpl(process.debuggerContext,
                                                                         stackFrame.stackFrameProxy.threadProxy()) {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun threadAction(suspendContext: SuspendContextImpl) {
          classLoader = ClassLoadingUtils.getClassLoader(contextImpl, process)
          val bytes = ConsumerExtractor().extractConsumer()
          ClassLoadingUtils.defineClass("com.intellij.debugger.streams.breakpoints.MyConsumerTest", bytes, contextImpl, process,
                                        classLoader)
          args.add(virtualMachine.mirrorOf(qName))
          if (classLoader != null) {
            forNameMethod = DebuggerUtils.findMethod(classClassType, "forName",
                                                     "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;")
            args.add(virtualMachine.mirrorOf(true))
            args.add(classLoader!!)
          }
          else {
            forNameMethod = DebuggerUtils.findMethod(classClassType, "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
          }
          val classReference = classClassType.invokeMethod(event.thread(), forNameMethod, args, MethodImpl.SKIP_ASSIGNABLE_CHECK)
          if (classReference is ClassObjectReference) {
            refType = classReference.reflectedType()
            if (classLoader is ClassLoaderReferenceImpl) {
              (classLoader as ClassLoaderReferenceImpl).addVisible(refType)
            }
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
    return classLoader
  }

  private fun reformatArrayName(className: String): String {
    var className = className
    if (className.indexOf('[') == -1) return className
    var dims = 0
    while (className.endsWith("[]")) {
      className = className.substring(0, className.length - 2)
      dims++
    }
    val buffer = StringBuilder()
    StringUtil.repeatSymbol(buffer, '[', dims)
    val primitiveSignature = JVMNameUtil.getPrimitiveSignature(className)
    if (primitiveSignature != null) {
      buffer.append(primitiveSignature)
    }
    else {
      buffer.append('L')
      buffer.append(className)
      buffer.append(';')
    }
    return buffer.toString()
  }
}