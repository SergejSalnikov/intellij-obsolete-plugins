// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.guice.model;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * What a line marker pass sees: the index, with the entries of the highlighted file taken from its current PSI
 * when the index is behind on that file (see {@link GuiceFileOverlay}). The view only reads.
 *
 * <p>The queries do not restore the PSI elements of the entries. They test only that the file of an entry
 * exists ({@link GuiceEntry#isInValidFile}). The gutter icon restores the targets when the user asks for them.
 *
 * @see GuiceProjectModel#getNavigationView
 */
final class GuiceNavigationView {
  private final @NotNull GuiceNavigationIndex myIndex;
  private final @Nullable GuiceFileOverlay myOverlay;

  GuiceNavigationView(@NotNull GuiceNavigationIndex index, @Nullable GuiceFileOverlay overlay) {
    myIndex = index;
    myOverlay = overlay;
  }

  /** @see GuiceNavigationIndex#findEntriesByAnchor(PsiElement) */
  @NotNull Set<GuiceEntry> findEntriesByAnchor(@NotNull PsiElement anchor) {
    return myIndex.findEntriesByAnchor(anchor, myOverlay);
  }

  /** @see GuiceNavigationIndex#findCounterparts(GuiceEntry) */
  @NotNull Set<GuiceEntry> findCounterparts(@NotNull GuiceEntry entry) {
    return myIndex.findCounterparts(entry, myOverlay, GuiceEntry::isInValidFile);
  }

  /** @see GuiceNavigationIndex#findByKey(GuiceBindingKey, EntryRole) */
  @NotNull Set<GuiceEntry> findByKey(@NotNull GuiceBindingKey key, @NotNull EntryRole role) {
    return myIndex.findByKey(key, role, myOverlay, GuiceEntry::isInValidFile);
  }
}
