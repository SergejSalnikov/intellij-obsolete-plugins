// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.guice.model;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.guice.GuiceBundle;
import com.intellij.guice.GuiceIcons;
import com.intellij.guice.model.renderers.GuiceEntryTargetRenderer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.UastUtils;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Class-level gutter icon: shows which {@code bind()} calls reference this class.
 *
 * <p>Uses the unified {@link GuiceNavigationIndex}: constructs a {@link GuiceBindingKey}
 * for the class type and finds all {@link EntryRole#BINDING_SITE} entries with a matching key.
 * It also finds the implementation references, for example {@code bind(Foo.class).to(ThisClass.class)}.
 */
public final class GuiceBindingClassAnnotator extends GuiceLineMarkerProviderBase {

  @Override
  public @NotNull String getId() {
    return "guice.bound.class";
  }

  @Override
  public @NotNull String getName() {
    return GuiceBundle.message("gutter.option.bound.class");
  }

  @Override
  public @NotNull Icon getIcon() {
    return GuiceIcons.GoogleSmall;
  }

  @Override
  protected void collectGuiceMarkers(@NotNull PsiElement psiElement,
                                     @NotNull PassContext context,
                                     @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    final PsiElement parent = psiElement.getParent();
    if (!(parent instanceof PsiNameIdentifierOwner nio)
        || !psiElement.equals(nio.getNameIdentifier())) {
      return;
    }

    final UClass uClass = UastContextKt.toUElement(parent, UClass.class);
    if (uClass == null) return;
    final PsiClass psiClass = uClass.getJavaPsi();
    if (psiClass.getQualifiedName() == null) return;

    // Find all BINDING_SITE entries whose type matches this class.
    GuiceBindingKey key = GuiceBindingKey.forClass(psiClass);
    GuiceNavigationView view = context.view();
    List<GuiceEntry> bindings = new ArrayList<>(view.findByKey(key, EntryRole.BINDING_SITE));
    // Bindings that use this class as the implementation: bind(Foo.class).to(ThisClass.class).
    for (GuiceEntry entry : view.findByKey(key, EntryRole.INJECTION_POINT)) {
      if (entry.isImplementationReference()) bindings.add(entry);
    }
    // The pass needs to know only that a target exists. The gutter icon restores and sorts the targets on demand.
    if (!hasTargetOutsideOf(bindings, psiClass)) return;

    List<GuiceEntry> entries = List.copyOf(bindings);
    // The lazy values keep the entries (smart pointers), the sort context and a pointer to the class, not PSI.
    // The pointer is to the source element: a pointer to the light class of a Kotlin class is a hard reference.
    SmartPsiElementPointer<PsiElement> classPointer = SmartPointerManager.createPointer(parent);
    GuiceEntryTargetRenderer.SortContext sortContext = GuiceEntryTargetRenderer.SortContext.of(psiElement);
    final GuiceGutterIconBuilder builder = new GuiceGutterIconBuilder(GuiceIcons.GoogleSmall);
    builder
      .setPopupTitle(GuiceBundle.message("GuiceClassAnnotator.popup.title"))
      .setTooltipText(GuiceBundle.message("GuiceClassAnnotator.popup.tooltip.text"))
      .setEmptyPopupText(GuiceBundle.message("gutter.no.targets"))
      .setTargetRenderer(() -> new GuiceEntryTargetRenderer(entries))
      .setTargets(lazyTargets(() -> {
        PsiElement classElement = classPointer.getElement();
        UClass currentClass = classElement != null ? UastContextKt.toUElement(classElement, UClass.class) : null;
        if (currentClass == null) return List.of();
        List<GuiceEntry> navigable = withoutSelfReferences(entries, currentClass.getJavaPsi());
        return GuiceEntryTargetRenderer.sortTargets(navigable, sortContext).targets();
      }));

    result.add(builder.createGuiceLineMarkerInfo(psiElement));
  }

  /**
   * Returns the bindings to navigate to. A self-reference is not one of them: an {@code @Inject} constructor
   * or an {@code @ImplementedBy} annotation of the class is a BINDING_SITE for the type of the class, but
   * the navigation from the class declaration to it is redundant.
   */
  private static @NotNull List<GuiceEntry> withoutSelfReferences(@NotNull List<GuiceEntry> bindings, @NotNull PsiClass psiClass) {
    List<GuiceEntry> result = new ArrayList<>(bindings.size());
    for (GuiceEntry binding : bindings) {
      PsiElement target = binding.getNavigationTarget();
      if (target != null && !isOwnConstructorOrAnnotation(target, psiClass)) {
        result.add(binding);
      }
    }
    return result;
  }

  /**
   * Tells if {@link #withoutSelfReferences} has one entry at least. It stops at the first one.
   */
  private static boolean hasTargetOutsideOf(@NotNull List<GuiceEntry> bindings, @NotNull PsiClass psiClass) {
    for (GuiceEntry binding : bindings) {
      PsiElement target = binding.getNavigationTarget();
      if (target != null && !isOwnConstructorOrAnnotation(target, psiClass)) return true;
    }
    return false;
  }

  private static boolean isOwnConstructorOrAnnotation(@NotNull PsiElement target, @NotNull PsiClass psiClass) {
    UElement uTarget = UastContextKt.toUElement(target);
    if (!(uTarget instanceof UMethod) && !(uTarget instanceof UAnnotation)) return false;
    UClass owner = UastUtils.getParentOfType(uTarget, UClass.class);
    return owner != null && psiClass.getManager().areElementsEquivalent(owner.getJavaPsi(), psiClass);
  }
}
