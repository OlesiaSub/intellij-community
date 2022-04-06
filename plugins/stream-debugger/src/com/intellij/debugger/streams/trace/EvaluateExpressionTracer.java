// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.StreamDebuggerBundle;
import com.intellij.debugger.streams.breakpoints.BreakpointSetter;
import com.intellij.debugger.streams.breakpoints.MethodExitProcessor;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluateExpressionTracer implements StreamTracer {
  private final XDebugSession mySession;
  private final TraceExpressionBuilder myExpressionBuilder;
  private final TraceResultInterpreter myResultInterpreter;

  public EvaluateExpressionTracer(@NotNull XDebugSession session,
                                  @NotNull TraceExpressionBuilder expressionBuilder,
                                  @NotNull TraceResultInterpreter interpreter) {
    mySession = session;
    myExpressionBuilder = expressionBuilder;
    myResultInterpreter = interpreter;
  }

  public void traverseBreakpoints(@NotNull List<PsiMethod> chainReferences, StreamChain chain) {
    BreakpointSetter bs = new BreakpointSetter(mySession.getProject());

    // for testing
    var file = chain.getContext().getContainingFile();
    Visitor v = new Visitor();
    file.accept(v);
    var methods = v.getPsiMethods();
    for (var m : methods) {
      int offset = m.getTextOffset();
      bs.setBreakpoint(m.getContainingFile(), offset);
    }


    // setting all bps to actual stream methods
    //for (int i = 0; i < chainReferences.toArray().length; i++) {
    //  int offset = chainReferences.get(i).getTextOffset();
    //  bs.setBreakpoint(chainReferences.get(i).getContainingFile(), offset);
    //}
    mySession.getDebugProcess().resume(mySession.getSuspendContext());

    mySession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        ApplicationManager.getApplication().invokeLater(() -> {
          final JavaStackFrame stackFrame = (JavaStackFrame) mySession.getCurrentStackFrame();
          assert stackFrame != null;
          MethodExitProcessor methodExitProcessor = new MethodExitProcessor((DebugProcessImpl) stackFrame.getDescriptor().getDebugProcess(), stackFrame);
          PsiManager m = new PsiManagerImpl(mySession.getProject());
          assert stackFrame.getSourcePosition() != null;
          var r = m.findFile(stackFrame.getSourcePosition().getFile());
          assert r != null;
          methodExitProcessor.mainFun(r);
          mySession.getDebugProcess().resume(mySession.getSuspendContext());
        });
      }
    });
  }

  public class Visitor extends PsiRecursiveElementVisitor {

    private Set<PsiMethod> psiMethods = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {

      if (element instanceof PsiMethod) {
        psiMethods.add((PsiMethod) element);
      }

      super.visitElement(element);
    }

    public Set<PsiMethod> getPsiMethods() {
      return psiMethods;
    }
  }

  @Override
  public void trace(@NotNull StreamChain chain, @NotNull TracingCallback callback, @NotNull List<PsiMethod> chainReferences) {
    traverseBreakpoints(chainReferences, chain);
    if (true) return;
    final String streamTraceExpression = myExpressionBuilder.createTraceExpression(chain);
    final XStackFrame stackFrame = mySession.getCurrentStackFrame();
    final XDebuggerEvaluator evaluator = mySession.getDebugProcess().getEvaluator();
    if (stackFrame != null && evaluator != null) {
      evaluator.evaluate(XExpressionImpl.fromText(streamTraceExpression, EvaluationMode.CODE_FRAGMENT), new XEvaluationCallbackBase() {
        @Override
        public void evaluated(@NotNull XValue result) {
          if (result instanceof JavaValue) {
            final Value reference = ((JavaValue)result).getDescriptor().getValue();
            if (reference instanceof ArrayReference) {
              final TracingResult interpretedResult;
              try {
                interpretedResult = myResultInterpreter.interpret(chain, (ArrayReference)reference);
              }
              catch (Throwable t) {
                callback.evaluationFailed(streamTraceExpression,
                                          StreamDebuggerBundle.message("evaluation.failed.cannot.interpret.result", t.getMessage()));
                throw t;
              }
              final EvaluationContextImpl context = ((JavaValue)result).getEvaluationContext();
              callback.evaluated(interpretedResult, context);
              return;
            }

            if (reference instanceof ObjectReference) {
              final ReferenceType type = ((ObjectReference)reference).referenceType();
              if (type instanceof ClassType) {
                ClassType classType = (ClassType)type;
                while (classType != null && !CommonClassNames.JAVA_LANG_THROWABLE.equals(classType.name())) {
                  classType = classType.superclass();
                }

                if (classType != null) {
                  final String exceptionMessage = tryExtractExceptionMessage((ObjectReference)reference);
                  final String description = "Evaluation failed: " + type.name() + " exception thrown";
                  final String descriptionWithReason = exceptionMessage == null ? description : description + ": " + exceptionMessage;
                  callback.evaluationFailed(streamTraceExpression, descriptionWithReason);
                  return;
                }
              }
            }
          }

          callback.evaluationFailed(streamTraceExpression, StreamDebuggerBundle.message("evaluation.failed.unknown.result.type"));
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          callback.compilationFailed(streamTraceExpression, errorMessage);
        }
      }, stackFrame.getSourcePosition());
    }
  }

  @Nullable
  private static String tryExtractExceptionMessage(@NotNull ObjectReference exception) {
    final ReferenceType type = exception.referenceType();
    final Field messageField = type.fieldByName("detailMessage");
    if (messageField == null) return null;
    final Value message = exception.getValue(messageField);
    if (message instanceof StringReference) {
      return ((StringReference)message).value();
    }

    return null;
  }
}
