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
    private final String enumName;
    private boolean oneOfMultipleTypes;
    private Collection<Named<PsiField>> namedFields;
    private PsiType type;

    public ConvertConstantsToEnumFix(String enumName, Collection<Named<PsiField>> namedFields, PsiType type, boolean oneOfMultipleTypes) {
        this.enumName = enumName;
        this.namedFields = namedFields;
        this.type = type;
        this.oneOfMultipleTypes = oneOfMultipleTypes;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
        return "Create enum named '" + enumName + "'"+(oneOfMultipleTypes ? " from "+type.getPresentableText()+" constants":"");
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return getName();
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor problemDescriptor) {
        final PsiField psiField = (PsiField) problemDescriptor.getPsiElement();
        final PsiClass psiClass = psiField.getContainingClass();
        new WriteCommandAction<Object>(project, psiField.getContainingFile()) {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
                final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
                Collection<Named<PsiField>> namedFields = ConvertConstantsToEnumFix.this.namedFields;
                SortedMap<Integer, Named<PsiField>> initialLiteralsInSequence = getInitialLiteralsInSequence(namedFields);
                Integer initialLiteralInSequence = null;
                if (null != initialLiteralsInSequence) {
                    initialLiteralInSequence = initialLiteralsInSequence.firstKey();
                    namedFields = initialLiteralsInSequence.values();
                }
                PsiClass anEnum = createEnum(psiElementFactory, psiClass, initialLiteralInSequence);
                for (Named<PsiField> namedField : namedFields) {
                    String enumConstantName = namedField.getName();
                    PsiField psiField = namedField.getValue();
                    String enumConstantText = enumConstantName;
                    if (initialLiteralsInSequence == null) {
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

    @NotNull
    private PsiClass createEnum(PsiElementFactory psiElementFactory, PsiClass psiClass, Integer initialLiteralInSequence) {
        PsiClass anEnum = (PsiClass) psiClass.add(psiElementFactory.createEnum(enumName));
        PsiMethod valueMethod = (PsiMethod) anEnum.add(psiElementFactory.createMethod("value", type, anEnum));
        String valueMethodBody;
        if (initialLiteralInSequence == null) {
            anEnum.add(psiElementFactory.createField("value", type));
            for (PsiType type : getFieldTypes(namedFields)) {
                PsiMethod constructor = addConstructor(psiElementFactory, anEnum, type);
                addMethodStatement(psiElementFactory, constructor, "this.value = value;");
            }
            valueMethodBody = "return value;";
        } else if (initialLiteralInSequence == 0) {
            valueMethodBody = "return ordinal();";
        } else {
            valueMethodBody = "return ordinal()+" + initialLiteralInSequence + ";";
        }
        addMethodStatement(psiElementFactory, valueMethod, valueMethodBody);
        return anEnum;
    }

    @NotNull
    private Set<PsiType> getFieldTypes(Collection<Named<PsiField>> namedFields) {
        Set<PsiType> types = new HashSet<PsiType>();
        for (Named<PsiField> namedField : namedFields) {
            types.add(namedField.getValue().getType());
        }
        return types;
    }

    private PsiMethod addConstructor(PsiElementFactory psiElementFactory, PsiClass anEnum, PsiType psiType) {
        PsiMethod constructor = (PsiMethod) anEnum.add(psiElementFactory.createConstructor());
        constructor.getParameterList().replace(psiElementFactory.createParameterList(new String[]{"value"}, new PsiType[]{psiType}));
        return constructor;
    }

    private SortedMap<Integer, Named<PsiField>> getInitialLiteralsInSequence(Collection<Named<PsiField>> input) {
        NavigableMap<Integer, Named<PsiField>> namedFields = sortedByInitializer(input);
        if (null != namedFields && completeSequence(namedFields.navigableKeySet())) {
            return namedFields;
        }
        return null;
    }

    private boolean completeSequence(SortedSet<Integer> initialLiterals) {
        int firstValue = initialLiterals.first();
        int lastValue = initialLiterals.last();
        return lastValue - firstValue == initialLiterals.size() - 1;
    }

    @Nullable
    private NavigableMap<Integer, Named<PsiField>> sortedByInitializer(Collection<Named<PsiField>> collection) {
        NavigableMap<Integer,Named<PsiField>> result = new TreeMap<Integer, Named<PsiField>>();
        for (Named<PsiField> namedField : collection) {
            Integer intInitializer = getIntInitializer(namedField.getValue());
            if (intInitializer == null) {
                return null;
            }
            result.put(intInitializer, namedField);
        }
        return result;
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
