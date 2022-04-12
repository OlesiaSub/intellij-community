// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.CommonClassNames
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.jdi.ClassLoaderReferenceImpl
import com.jetbrains.jdi.MethodImpl
import com.sun.jdi.*
import com.sun.jdi.event.MethodExitEvent

class MethodExitEventHandler {

    // пока не использую (не могу загрузить класс, скорее всего из-за проблем с classpath), код взяла в каком-то из файлов дебаггера
    @Throws(InvocationException::class, ClassNotLoadedException::class, IncompatibleThreadStateException::class,
            InvalidTypeException::class, EvaluateException::class)
    fun loadClass(qName: String, classLoader: ClassLoaderReference?, event: MethodExitEvent): ReferenceType? {
      var qName = qName
      //DebuggerManagerThreadImpl.assertIsManagerThread()
      qName = reformatArrayName(qName)
      val virtualMachine = event.virtualMachine()
      val classClassType = ContainerUtil.getFirstItem(virtualMachine.classesByName(CommonClassNames.JAVA_LANG_CLASS)) as ClassType
      val forNameMethod: Method?
      val args: MutableList<Value> = ArrayList()
      args.add(virtualMachine.mirrorOf(qName))
      if (classLoader != null) {
        forNameMethod = DebuggerUtils.findMethod(classClassType, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;")
        args.add(virtualMachine.mirrorOf(true))
        args.add(classLoader)
      }
      else {
        forNameMethod = DebuggerUtils.findMethod(classClassType, "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
      }
      val classReference = classClassType.invokeMethod(event.thread(), forNameMethod, args, MethodImpl.SKIP_ASSIGNABLE_CHECK)
      if (classReference is ClassObjectReference) {
        val refType = classReference.reflectedType()
        if (classLoader is ClassLoaderReferenceImpl) {
          classLoader.addVisible(refType)
        }
        return refType
      }
      return null
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

    fun handleMethodExitEvent(event: MethodExitEvent) {
      val returnValue = event.returnValue()
      if (returnValue is ObjectReference) {
        ApplicationManager.getApplication().invokeLater {
          try {
            // Consumer consumerr есть в коде проекта, на котором я тестирую
            val field = event.virtualMachine().classesByName("StreamsTest").get(0).fieldByName("consumerr")
            val fieldValue = event.virtualMachine().classesByName("StreamsTest").get(0).getValues(listOf(field)).get(field)
            val ret = returnValue
              .invokeMethod(event.thread(),
                            returnValue.referenceType().methodsByName("peek")[0],
                            listOf(fieldValue),
                            0)
            event.thread().forceEarlyReturn(ret)
            event.request().disable()
          }
          catch (e: Exception) {
            e.printStackTrace()
          }
        }
      }
      println("Method " + event.method())
    }
}