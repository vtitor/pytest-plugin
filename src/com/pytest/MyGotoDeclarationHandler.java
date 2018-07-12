package com.pytest;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class MyGotoDeclarationHandler extends GotoDeclarationHandlerBase {
  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement source, Editor editor) {
    if (source != null && isPyFuncParameter(source)) {
      PyFunction fixture = findFixtureWithSameName(source);
      return fixture != null ? fixture : findFixtureWithCustomName(source);
    }
    return null;
  }

  @Nullable
  private PyFunction findFixtureWithSameName(@NotNull PsiElement source) {
    String sourceDirPath = getDirPath(source);
    String text = source.getText();
    Project project = source.getProject();

    PyFunction conftestFunc = null, outSideFunc = null;

    for (PyFunction function : findFunctions(text, project))
      if (isFixture(function)) {
        PsiFile funcFile = function.getContainingFile();
        String funcDirPath = getDirPath(function);

        if (funcFile.equals(source.getContainingFile())) return function;

        if (isConftest(funcFile)) {
          if (sourceDirPath.contains(funcDirPath)) {
            if (conftestFunc != null) {
              String conftestFuncDirPath = getDirPath(conftestFunc);
              // pick nearest
              if (funcDirPath.contains(conftestFuncDirPath)) conftestFunc = function;
            } else conftestFunc = function;
          }
        } else {
          outSideFunc = function;
        }
      }

    return conftestFunc != null ? conftestFunc : outSideFunc;
  }

  @Nullable
  private PyFunction findFixtureWithCustomName(@NotNull PsiElement source) {
    Project project = source.getProject();
    String text = source.getText();

    for (String funcKey : PyFunctionNameIndex.allKeys(project))
      if (!funcKey.equals(text))
        for (PyFunction function : findFunctions(funcKey, project))
          if (isFixture(function) && hasNameParam(function, text)) return function;

    return null;
  }

  private boolean isPyFuncParameter(@NotNull PsiElement source) {
    return source.getLanguage() instanceof PythonLanguage
        && source.getParent() instanceof PyNamedParameter;
  }

  private boolean isFixture(@NotNull PyFunction function) {
    PyDecoratorList decorators = function.getDecoratorList();
    if (decorators != null)
      for (PyDecorator decorator : decorators.getDecorators()) {
        String text = decorator.getText();
        if (text.startsWith("@fixture")
            || text.startsWith("@pytest.fixture")
            || text.startsWith("@yield_fixture")
            || text.startsWith("@pytest.yield_fixture")) return true;
      }
    return false;
  }

  private boolean isConftest(@NotNull PsiFile file) {
    return file.getName().equals("conftest.py");
  }

  private boolean hasNameParam(@NotNull PyFunction fixture, String name) {
    PyDecoratorList decorators = fixture.getDecoratorList();
    String regex = String.format(".+fixture\\((.|\\n)*name\\s*=\\s*['\\\"]%s['\\\"](.|\\n)+", name);
    if (decorators != null)
      for (PyDecorator decorator : decorators.getDecorators())
        if (decorator.getText().matches(regex)) return true;
    return false;
  }

  @NotNull
  private Collection<PyFunction> findFunctions(String text, Project project) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    return PyFunctionNameIndex.find(text, project, scope);
  }

  @NotNull
  private String getDirPath(@NotNull PsiElement element) {
    return element.getContainingFile().getContainingDirectory().getVirtualFile().getPath();
  }
}
