// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.ui.breakpoints.FilteredRequestor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.*
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.request.BreakpointRequest
import com.sun.jdi.request.ClassPrepareRequest
import com.sun.jdi.request.MethodExitRequest

// unused
class MethodExitProcessor(private val myDebugProcess: DebugProcessImpl, private val myStackFrame: JavaStackFrame?) {
  private var debugClass: String? = null

  fun enableClassPrepareRequest(vm: VirtualMachineProxyImpl) {
    val classPrepareRequest: ClassPrepareRequest = vm.eventRequestManager().createClassPrepareRequest()
    classPrepareRequest.addClassFilter(debugClass ?: return)
    classPrepareRequest.enable()
    classPrepareRequest.setEnabled(true)
    println("enabled? ${classPrepareRequest.isEnabled}")
    classPrepareRequest.isEnabled
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

  fun shit(file: PsiFile, myVm: VirtualMachineProxyImpl, virtualMachineProxy: VirtualMachineProxyImpl) {
    //val myVm = DebugProcessEvents(file.project).virtualMachineProxy
    //val myVm = myDebugProcess.virtualMachineProxy.virtualMachine
    myVm.resume()
    println("my vm is $myVm")
    println("here ${myVm.eventQueue()}")
    println("here 1 ${myVm.eventQueue().remove()}")
    val eventSet: EventSet? = myVm.eventQueue().remove()
    if (eventSet == null || eventSet.isEmpty()) return
    println("before passed")
    eventSet.forEach { println(it) }
    println("passed")
    for (event in eventSet) {
      println("event ${event.toString()}")
      if (event is MethodExitEvent) {
        println("ret val ${event.returnValue()}")
      }
      myVm.resume()
    }
  }

  fun mainFun(file: PsiFile) {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    val v = Visitor()
    file.accept(v)
    val p = DebuggerManagerEx.getInstanceEx(file.project).context.contextElement
    val pc = PsiTreeUtil.getParentOfType(p, PsiClass::class.java) ?: return
    this.debugClass = pc.name
    try {
      myDebugProcess.managerThread.schedule(object : DebuggerContextCommandImpl(myDebugProcess.debuggerContext,
                                                                                myStackFrame!!.stackFrameProxy.threadProxy()) {
        override fun getPriority(): PrioritizedTask.Priority {
          return PrioritizedTask.Priority.HIGH
        }

        override fun threadAction(suspendContext: SuspendContextImpl) {
          System.out.println("here")
          val myVm = myDebugProcess.virtualMachineProxy
          enableClassPrepareRequest(myVm)
          myVm.resume()
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
