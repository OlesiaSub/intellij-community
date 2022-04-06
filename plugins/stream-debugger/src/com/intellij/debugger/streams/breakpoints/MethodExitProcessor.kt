// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.*
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.request.ClassPrepareRequest
import com.sun.jdi.request.MethodExitRequest


class MethodExitProcessor(private val myDebugProcess: DebugProcessImpl, private val myStackFrame: JavaStackFrame?) {
  private var debugClass: String? = null

  fun enableClassPrepareRequest(vm: VirtualMachine) {
    val classPrepareRequest: ClassPrepareRequest = vm.eventRequestManager().createClassPrepareRequest()
    classPrepareRequest.addClassFilter(debugClass ?: return)
    classPrepareRequest.enable()
    val methodExitRequest: MethodExitRequest = vm.eventRequestManager().createMethodExitRequest()
    methodExitRequest.addClassFilter(debugClass)
    methodExitRequest.enable()
  }

  @Throws(IncompatibleThreadStateException::class, AbsentInformationException::class)
  fun displayVariables() {
    val stackFrame = myStackFrame?.stackFrameProxy?.stackFrame ?: return
    if (stackFrame.location() == null || debugClass == null) return
    if (stackFrame.location().toString().contains(debugClass!!)) {
      val visibleVariables: Map<LocalVariable, Value> = stackFrame.getValues(stackFrame.visibleVariables())
      println("Variables at " + stackFrame.location().toString() + " > ")
      for ((key, value) in visibleVariables) {
        println(key.name() + " = " + value)
      }
    }
  }

  fun mainFun(file: PsiFile) {
    val v = Visitor()
    file.accept(v)
    val p = DebuggerManagerEx.getInstanceEx(file.project).context.contextElement
    val pc = PsiTreeUtil.getParentOfType(p, PsiClass::class.java) ?: return
    println("class: " + pc.name)
    this.debugClass = pc.name
    try {
      myDebugProcess.managerThread.schedule(object : DebuggerContextCommandImpl(myDebugProcess.debuggerContext,
                                                                                myStackFrame!!.stackFrameProxy.threadProxy()) {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun threadAction(suspendContext: SuspendContextImpl) {
          val myVm = myDebugProcess.virtualMachineProxy.virtualMachine
          enableClassPrepareRequest(myVm)
          displayVariables()
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

class Visitor : PsiRecursiveElementVisitor() {
  private val psiClasses: MutableSet<PsiClass> = mutableSetOf()
  override fun visitElement(element: PsiElement) {
    if (element is PsiClass) {
      psiClasses.add(element)
    }
    super.visitElement(element)
  }

  fun getPsiClasses(): Set<PsiClass> {
    return psiClasses
  }
}
