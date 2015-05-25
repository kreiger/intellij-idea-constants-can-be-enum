package com.linuxgods.kreiger.intellij.idea.constants.enumclass;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class ConvertConstantsToEnumFix implements LocalQuickFix {
    private final PsiClass psiClass;
    private final String enumName;
    private Collection<Named<PsiField>> namedFields;
    private final PsiType psiType;

    public ConvertConstantsToEnumFix(PsiClass aClass, String enumName, Collection<Named<PsiField>> namedFields, PsiType psiType) {
        this.psiClass = aClass;
        this.enumName = enumName;
        this.namedFields = namedFields;
        this.psiType = psiType;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
        return "Create enum named '" + enumName + "'";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Create enum named '" + enumName + "'";
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor problemDescriptor) {
        new WriteCommandAction<Object>(project, psiClass.getContainingFile()) {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
                final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
                PsiClass anEnum = (PsiClass) psiClass.add(psiElementFactory.createEnum(enumName));
                PsiMethod valueMethod = (PsiMethod) anEnum.add(psiElementFactory.createMethod("value", psiType, anEnum));
                Integer initialLiteralInSequence = getInitialLiteralInSequence();
                if (initialLiteralInSequence == null) {
                    anEnum.add(psiElementFactory.createField("value", psiType));
                    PsiMethod constructor = (PsiMethod) anEnum.add(psiElementFactory.createConstructor());
                    constructor.getParameterList().replace(psiElementFactory.createParameterList(new String[]{"value"}, new PsiType[]{psiType}));
                    addMethodStatement(psiElementFactory, constructor, "this.value = value;");
                    addMethodStatement(psiElementFactory, valueMethod, "return value;");
                } else if (initialLiteralInSequence == 0) {
                    addMethodStatement(psiElementFactory, valueMethod, "return ordinal();");
                } else {
                    addMethodStatement(psiElementFactory, valueMethod, "return ordinal()+"+initialLiteralInSequence+";");
                }
                for (Named<PsiField> namedField : namedFields) {
                    String enumConstantName = namedField.getName();
                    PsiField psiField = namedField.getValue();
                    String enumConstantText = enumConstantName;
                    if (initialLiteralInSequence == null) {
                        enumConstantText+= "(" + psiField.getInitializer().getText() + ")";
                    }
                    anEnum.add(psiElementFactory.createEnumConstantFromText(enumConstantText, anEnum));
                    PsiExpression enumConstantReference = psiElementFactory.createExpressionFromText(enumName + "." + enumConstantName + ".value()", psiClass);
                    psiField.setInitializer(enumConstantReference);
                }

                CodeStyleManager.getInstance(project).reformat(anEnum);
            }
        }.execute();
    }

    private Integer getInitialLiteralInSequence() {
        List<Named<PsiField>> namedFields;
        try {
            namedFields = sortedByInitializer(this.namedFields);
        } catch (NullPointerException e) {
            return null;
        }

        Integer initialLiteral = null;
        Integer previousLiteral = null;

        for (Named<PsiField> namedField : namedFields) {
            PsiField psiField = namedField.getValue();
            Integer intValue = getIntInitializer(psiField);
            if (initialLiteral == null) {
                initialLiteral = intValue;
                previousLiteral = intValue;
            } else if (!intValue.equals(++previousLiteral)) {
                return null;
            }
        }
        this.namedFields = namedFields;
        return initialLiteral;
    }

    @NotNull
    private List<Named<PsiField>> sortedByInitializer(Collection<Named<PsiField>> collection) {
        List<Named<PsiField>> list = new ArrayList<Named<PsiField>>(collection);
        Collections.sort(list, new Comparator<Named<PsiField>>() {
            @Override
            public int compare(Named<PsiField> o1, Named<PsiField> o2) {
                Integer i1 = getIntInitializer(o1.getValue());
                Integer i2 = getIntInitializer(o2.getValue());
                return i1.compareTo(i2);
            }
        });
        return list;
    }

    @Nullable
    private Integer getIntInitializer(PsiField psiField) {
        PsiExpression psiFieldInitializer = psiField.getInitializer();
        if (!(psiFieldInitializer instanceof PsiLiteralExpression)) {
            return null;
        }
        PsiLiteralExpression psiLiteralInitializer = (PsiLiteralExpression) psiFieldInitializer;
        Object value = psiLiteralInitializer.getValue();
        if (!(value instanceof Number)) {
            return null;
        }
        return ((Number)value).intValue();
    }

    private PsiElement addMethodStatement(PsiElementFactory psiElementFactory, PsiMethod valueMethod, String statement) {
        return valueMethod.getBody().add(psiElementFactory.createStatementFromText(statement, valueMethod.getBody()));
    }

}
