// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.guice;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.guice.inspections.MultipleInjectedConstructorsForClassInspection;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static com.google.common.truth.Truth.assertThat;

/**
 * Checks that the Guice inspections stay off in a module without Guice. The module of this test has JSR-330
 * ({@code javax.inject}) only, as a Dagger or a Spring project has.
 */
public class GuiceInspectionsWithoutGuiceTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor JSR330_ONLY =
    new DefaultLightProjectDescriptor() {
      @Override
      public Sdk getSdk() {
        return JavaSdk.getInstance().createJdk("Current JDK", System.getProperty("java.home"), false);
      }

      @Override
      public void configureModule(@NotNull Module module,
                                  @NotNull ModifiableRootModel model,
                                  @NotNull ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        Path jar = Path.of(PathManager.getJarPathForClass(Inject.class));
        PsiTestUtil.addLibrary(model, jar.getFileName().toString(), jar.getParent().toString(), jar.getFileName().toString());
      }
    };

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JSR330_ONLY;
  }

  public void testInspectionsDoNotRunInAModuleWithoutGuice() {
    myFixture.enableInspections(new MultipleInjectedConstructorsForClassInspection());
    PsiFile file = myFixture.configureByText("Client.java", """
      import javax.inject.Inject;
      public class Client {
        @Inject public Client() {}
        @Inject public Client(String s) {}
      }
      """);
    // Guice would report the two constructors. Without Guice in the module, the inspection is silent.
    myFixture.checkHighlighting();

    ProblemsHolder holder = new ProblemsHolder(InspectionManager.getInstance(getProject()), file, true);
    assertThat(new MultipleInjectedConstructorsForClassInspection().buildVisitor(holder, true))
      .isSameInstanceAs(PsiElementVisitor.EMPTY_VISITOR);
  }
}
