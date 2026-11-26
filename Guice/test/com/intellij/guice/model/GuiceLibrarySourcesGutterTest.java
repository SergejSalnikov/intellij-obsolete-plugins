// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.guice.model;

import com.intellij.guice.GuiceTestBase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertThat;

/**
 * Checks the gutter icons in a source file of a library. Such a file is in the content of no module, so the
 * module lookup for an element of the file finds nothing, and the providers need the module that uses the library.
 */
public class GuiceLibrarySourcesGutterTest extends GuiceTestBase {

  /** Creates a source file outside the project and returns the directory that holds {@code relativePath}. */
  private static VirtualFile createLibrarySourceRoot(String relativePath, String text) throws IOException {
    Path root = FileUtil.createTempDirectory("guice-library-sources", null).toPath();
    Path file = root.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
    VirtualFile sourceRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root);
    assertNotNull(sourceRoot);
    VfsUtil.markDirtyAndRefresh(false, true, true, sourceRoot);
    return sourceRoot;
  }

  /**
   * Attaches a source root to the library of the Guice jar until the end of the test, as "Attach Sources" does.
   * The root is a directory, or one source file.
   */
  private void attachSourceRootToGuiceLibrary(VirtualFile sourceRoot) {
    Module module = getModule();
    Project project = getProject();
    String url = sourceRoot.getUrl();
    updateGuiceLibrary(module, library -> library.addRoot(url, OrderRootType.SOURCES));
    Disposer.register(getTestRootDisposable(), () -> {
      updateGuiceLibrary(module, library -> library.removeRoot(url, OrderRootType.SOURCES));
      GuiceProjectModel.getInstance(project).resetForTests();
    });
    // The discovery searches need the indexes of the new root.
    IndexingTestUtil.waitUntilIndexesAreReady(project);
    GuiceProjectModel.getInstance(project).resetForTests();
  }

  private static void updateGuiceLibrary(Module module, Consumer<Library.ModifiableModel> change) {
    String jarName = Path.of(PathManager.getJarPathForClass(com.google.inject.Inject.class)).getFileName().toString();
    WriteAction.runAndWait(() -> {
      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (!(entry instanceof LibraryOrderEntry libraryEntry)) continue;
        Library library = libraryEntry.getLibrary();
        if (library == null) continue;
        if (!ContainerUtil.exists(library.getUrls(OrderRootType.CLASSES), url -> url.contains("/" + jarName))) continue;
        Library.ModifiableModel model = library.getModifiableModel();
        change.accept(model);
        model.commit();
        return;
      }
      fail("No library with " + jarName);
    });
  }

  /**
   * The discovery finds the module classes of {@code com.google.inject.util.Modules} in the jar. The navigation
   * from the compiled file leads to the attached source file of that name, so the model extracts the entries
   * from the source, and the source file shows them.
   */
  public void testGutterIconsInTheSourceFileOfALibrary() throws IOException {
    String relativePath = "com/google/inject/util/Modules.java";
    VirtualFile sourceRoot = createLibrarySourceRoot(relativePath, """
      package com.google.inject.util;

      import com.google.inject.AbstractModule;
      import com.google.inject.Inject;

      public final class Modules {
        interface LibService {}

        static class LibServiceImpl implements LibService {}

        static class LibClient {
          @Inject LibService service;
        }

        static class LibModule extends AbstractModule {
          @Override
          protected void configure() {
            bind(LibService.class).to(LibServiceImpl.class);
          }
        }
      }
      """);
    attachSourceRootToGuiceLibrary(sourceRoot);
    VirtualFile source = sourceRoot.findFileByRelativePath(relativePath);
    assertNotNull(source);
    // A library source file: in a library, and in the content of no module.
    assertThat(ProjectFileIndex.getInstance(getProject()).isInLibrarySource(source)).isTrue();
    assertThat(ModuleUtilCore.findModuleForFile(source, getProject())).isNull();
    getNavigationIndex();

    myFixture.openFileInEditor(source);
    // The injection point, and the class gutters of the bound class and of the implementation class.
    assertThat(gutterAnchors(TO_BINDINGS_TOOLTIP)).containsExactly("LibService", "LibServiceImpl", "service");
    assertThat(gutterAnchors(TO_INJECTION_POINTS_TOOLTIP)).containsExactly("bind");
    assertThat(gutterTargets(TO_INJECTION_POINTS_TOOLTIP)).containsExactly("@Inject LibService service;");
  }

  /**
   * A library can have single source files as roots. The class gutter of such a file shows the bindings
   * of the class in the project.
   */
  public void testClassGutterInASingleFileSourceRootOfALibrary() throws IOException {
    String relativePath = "com/google/inject/Stage.java";
    VirtualFile directory = createLibrarySourceRoot(relativePath, """
      package com.google.inject;

      public enum Stage {
        TOOL, DEVELOPMENT, PRODUCTION
      }
      """);
    VirtualFile source = directory.findFileByRelativePath(relativePath);
    assertNotNull(source);
    attachSourceRootToGuiceLibrary(source);
    myFixture.addFileToProject("StageModule.java", """
      import com.google.inject.AbstractModule;
      import com.google.inject.Stage;
      public class StageModule extends AbstractModule {
        @Override
        protected void configure() {
          bind(Stage.class).toInstance(Stage.PRODUCTION);
        }
      }
      """);
    assertThat(ProjectFileIndex.getInstance(getProject()).isInLibrarySource(source)).isTrue();
    getNavigationIndex();

    myFixture.openFileInEditor(source);
    assertThat(gutterAnchors(TO_BINDINGS_TOOLTIP)).containsExactly("Stage");
    assertThat(gutterTargets(TO_BINDINGS_TOOLTIP)).containsExactly("bind(Stage.class).toInstance(Stage.PRODUCTION)");
  }
}
