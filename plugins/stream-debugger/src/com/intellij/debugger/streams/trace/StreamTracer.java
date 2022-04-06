// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace;

import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface StreamTracer {
  //void trace(@NotNull StreamChain chain, @NotNull TracingCallback callback);
  void trace(@NotNull StreamChain chain, @NotNull TracingCallback callback, @NotNull List<PsiMethod> chainReferences);
}
