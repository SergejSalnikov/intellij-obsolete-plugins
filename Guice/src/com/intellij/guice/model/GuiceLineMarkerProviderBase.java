// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.guice.model;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared base of the Guice line marker providers. It tests that the module has Guice before it collects
 * markers, and it gives the providers a read-only view of the index for the highlighted file.
 *
 * <p>A provider only reads. The index is written by {@link GuiceBackgroundIndexUpdater} alone: when the index
 * is behind on the highlighted file, the view takes the entries of the file from its PSI
 * (see {@link GuiceProjectModel#getNavigationView}).
 */
abstract class GuiceLineMarkerProviderBase extends RelatedItemLineMarkerProvider {

  /**
   * What the providers share in one call of {@link #collectNavigationMarkers(List, Collection, boolean)}:
   * all elements of a call belong to one file. The view is created on first use, so a file without
   * a Guice-relevant element does not reach the model.
   */
  static final class PassContext {
    private final @NotNull Module myModule;
    private final @NotNull PsiFile myFile;
    private GuiceNavigationView myView;

    PassContext(@NotNull Module module, @NotNull PsiFile file) {
      myModule = module;
      myFile = file;
    }

    @NotNull GuiceNavigationView view() {
      GuiceNavigationView view = myView;
      if (view == null) {
        myView = view = GuiceProjectModel.getInstance(myModule.getProject()).getNavigationView(myModule, myFile);
      }
      return view;
    }
  }

  @Override
  public final void collectNavigationMarkers(@NotNull List<? extends PsiElement> elements,
                                             @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result,
                                             boolean forNavigation) {
    if (elements.isEmpty()) return;
    PsiElement first = elements.getFirst();
    Module module = ModuleUtilCore.findModuleForPsiElement(first);
    // Test the module before the service exists: a project without Guice never creates the service.
    if (module == null || !GuiceProjectModel.isGuiceAvailable(module)) return;
    PsiFile file = first.getContainingFile();
    if (file == null) return;

    PassContext context = new PassContext(module, file);
    // The loop of RelatedItemLineMarkerProvider, with the context of this call.
    for (int i = 0, size = elements.size(); i < size; i++) {
      PsiElement element = elements.get(i);
      collectGuiceMarkers(element, context, result);
      if (forNavigation && element instanceof PsiNameIdentifierOwner owner) {
        PsiElement nameIdentifier = owner.getNameIdentifier();
        if (nameIdentifier != null && !elements.contains(nameIdentifier)) {
          collectGuiceMarkers(nameIdentifier, context, result);
        }
      }
    }
  }

  /**
   * Adds the markers of one element. The implementation only reads; it gets the entries from {@code context}.
   */
  protected abstract void collectGuiceMarkers(@NotNull PsiElement element,
                                              @NotNull PassContext context,
                                              @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result);

  /**
   * Tells if one entry at least has a navigation target. It restores as few targets as possible: the pass
   * needs to know only that a gutter icon has a target, and the icon restores the others on demand.
   */
  static boolean hasNavigationTarget(@NotNull Collection<GuiceEntry> entries) {
    for (GuiceEntry entry : entries) {
      if (entry.getNavigationTarget() != null) return true;
    }
    return false;
  }

  /**
   * Wraps the computation of the targets of a gutter icon. It runs when the user clicks the icon or uses
   * Navigate | Related Symbol, not in the pass, so it must not capture PSI. That can be in dumb mode.
   */
  static @NotNull NotNullLazyValue<Collection<? extends PsiElement>> lazyTargets(
    @NotNull Supplier<? extends Collection<? extends PsiElement>> targets) {
    return NotNullLazyValue.volatileLazy(() -> {
      try {
        return targets.get();
      }
      catch (IndexNotReadyException e) {
        return List.of();
      }
    });
  }
}
