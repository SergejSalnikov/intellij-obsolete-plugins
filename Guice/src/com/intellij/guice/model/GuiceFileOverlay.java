// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.guice.model;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The entries of one file as its current PSI gives them.
 *
 * <p>A highlighting pass of an edited file runs before the background update of {@link GuiceNavigationIndex}.
 * The pass must not write to the index, so it extracts the entries of its file and gives them to the index
 * queries as an overlay: in a query they replace the entries that the index stores for the file.
 *
 * @see GuiceNavigationView
 */
final class GuiceFileOverlay {
  private final @NotNull String myPath;
  private final @NotNull GuiceEntryProducer.FileExtractionResult myResult;
  private final @NotNull Map<GuiceEntry.AnchorKey, Set<GuiceEntry>> myEntriesByAnchor = new HashMap<>();
  private final @NotNull Map<String, Set<GuiceEntry>> myEntriesByTypeFqn;

  GuiceFileOverlay(@NotNull String path, @NotNull GuiceEntryProducer.FileExtractionResult result) {
    myPath = path;
    myResult = result;
    myEntriesByTypeFqn = GuiceNavigationIndex.groupByTypeFqn(result.entries());
    for (GuiceEntry entry : result.entries()) {
      GuiceEntry.AnchorKey anchorKey = entry.getAnchorKey();
      // The same entries that the index keys by anchor.
      if (entry.getKey().getTypeFqn() == null || anchorKey == null) continue;
      myEntriesByAnchor.computeIfAbsent(anchorKey, k -> new HashSet<>()).add(entry);
    }
  }

  /** The path of the file that the entries come from. */
  @NotNull String path() {
    return myPath;
  }

  /** The extraction result, which the background update stores in the index when the file did not change again. */
  @NotNull GuiceEntryProducer.FileExtractionResult result() {
    return myResult;
  }

  @NotNull Set<GuiceEntry> entriesAt(@NotNull GuiceEntry.AnchorKey anchorKey) {
    Set<GuiceEntry> entries = myEntriesByAnchor.get(anchorKey);
    return entries != null ? entries : Set.of();
  }

  @NotNull Set<GuiceEntry> entriesOfType(@NotNull String typeFqn) {
    Set<GuiceEntry> entries = myEntriesByTypeFqn.get(typeFqn);
    return entries != null ? entries : Set.of();
  }
}
