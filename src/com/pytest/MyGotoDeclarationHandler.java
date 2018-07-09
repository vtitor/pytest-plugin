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
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class MyGotoDeclarationHandler extends GotoDeclarationHandlerBase {
  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement source, Editor editor) {
    if (source != null && isPyFuncParameter(source)) {
      String sourceDirPath = getDirPath(source);
      PyFunction conftestFunc = null, outSideFunc = null;

      for (PyFunction function : findFunctions(source))
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
    return null;
  }

  private boolean isPyFuncParameter(PsiElement source) {
    return source.getLanguage() instanceof PythonLanguage
        && source.getParent() instanceof PyNamedParameter;
  }

  private boolean isFixture(PyFunction function) {
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

  private boolean isConftest(PsiFile file) {
    return file.getName().equals("conftest.py");
  }

  private Collection<PyFunction> findFunctions(PsiElement source) {
    Project project = source.getProject();
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    String text = source.getText();
    return PyFunctionNameIndex.find(text, project, scope);
  }

  private String getDirPath(PsiElement element) {
    return element.getContainingFile().getContainingDirectory().getVirtualFile().getPath();
  }
}
