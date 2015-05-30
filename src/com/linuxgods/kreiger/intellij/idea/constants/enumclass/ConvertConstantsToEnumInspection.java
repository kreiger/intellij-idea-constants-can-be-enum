package com.linuxgods.kreiger.intellij.idea.constants.enumclass;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class ConvertConstantsToEnumInspection extends BaseLocalInspectionTool {
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkClass(@NotNull final PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
        List<PsiField> fields = asList(psiClass.getFields());
        List<ProblemDescriptor> problemDescriptors = new ArrayList<ProblemDescriptor>();
        final Map<List<String>, List<Named<PsiField>>> enumsFields = createEnumFields(psiClass, fields);
        for (Map.Entry<List<String>, List<Named<PsiField>>> enumFieldsEntry : enumsFields.entrySet()) {
            List<Named<PsiField>> namedFields = enumFieldsEntry.getValue();
            if (namedFields.size() >= 2) {
                List<String> enumNameComponents = enumFieldsEntry.getKey();
                for (Named<PsiField> namedField : namedFields) {
                    PsiType type = namedField.getValue().getType();
                    String enumName = getEnumName(enumNameComponents);
                    Collection<Named<PsiField>> fieldsWithType = fieldsWithType(namedFields, type);
                    List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>(singletonList(new ConvertConstantsToEnumFix(enumName, namedFields, type, false)));
                    if (fieldsWithType.size() < namedFields.size()) {
                        fixes.add(new ConvertConstantsToEnumFix(enumName, fieldsWithType, type, true));
                    }
                    problemDescriptors.add(manager.createProblemDescriptor(namedField.getValue(), (TextRange) null, "Create enum named '" + enumName + "'", ProblemHighlightType.WEAK_WARNING, isOnTheFly,
                            fixes.toArray(new LocalQuickFix[fixes.size()])));
                }
            }
        }
        return problemDescriptors.toArray(new ProblemDescriptor[problemDescriptors.size()]);
    }

    private Collection<Named<PsiField>> fieldsWithType(Collection<Named<PsiField>> namedFields, PsiType type) {
        Collection<Named<PsiField>> result = new ArrayList<Named<PsiField>>();
        for (Named<PsiField> namedField : namedFields) {
            if (namedField.getValue().getType().equals(type)) {
                result.add(namedField);
            }
        }
        return result;
    }

    @NotNull
    private Map<List<String>, List<Named<PsiField>>> createEnumFields(@NotNull PsiClass psiClass, List<PsiField> fields) {
        final Map<List<String>, List<Named<PsiField>>> enumsFields = new HashMap<List<String>, List<Named<PsiField>>>();
        for (PsiField field : fields) {
            if (!ExpressionUtils.isConstant(field)) {
                continue;
            }
            String fieldName = field.getName();
            List<String> fieldNameComponents = split(fieldName);
            for (int i = 1; i <= fieldNameComponents.size() - 1; i++) {
                List<String> prefix = fieldNameComponents.subList(0, i);
                List<String> suffix = fieldNameComponents.subList(i, fieldNameComponents.size());
                addEnumField(psiClass, enumsFields, field, prefix, getEnumConstantName(suffix));
                addEnumField(psiClass, enumsFields, field, suffix, getEnumConstantName(prefix));
            }
        }
        return enumsFields;
    }

    @NotNull
    private List<String> split(String fieldName) {
        String[] split = fieldName.split("_|(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{Lu}\\p{Ll})");
        List<String> result = new ArrayList<String>();
        for (String s : split) {
            if (!s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }

    private void addEnumField(@NotNull PsiClass psiClass, Map<List<String>, List<Named<PsiField>>> enumsFields, PsiField field, List<String> enumName, String enumConstantName) {
        if (!enumName.isEmpty() && !enumConstantName.isEmpty() && null == psiClass.findInnerClassByName(getEnumName(enumName), false)) {
            getFields(enumsFields, enumName).add(new Named<PsiField>(enumConstantName, field));
        }
    }

    private String getEnumConstantName(List<String> suffixComponents) {
        StringBuilder enumConstantName = new StringBuilder();
        for (Iterator<String> iterator = suffixComponents.iterator(); iterator.hasNext(); ) {
            String suffixComponent = iterator.next();
            if (suffixComponent.isEmpty()) {
                continue;
            }
            enumConstantName.append(suffixComponent.toUpperCase());
            if (iterator.hasNext()) {
                enumConstantName.append('_');
            }
        }
        return enumConstantName.toString();
    }

    private String getEnumName(List<String> prefixComponents) {
        StringBuilder enumName = new StringBuilder();
        for (String prefixComponent : prefixComponents) {
            if (prefixComponent.isEmpty()) {
                continue;
            }
            enumName.append(Character.toUpperCase(prefixComponent.charAt(0)));
            if (prefixComponent.length() > 1) {
                enumName.append(prefixComponent.substring(1).toLowerCase());
            }
        }
        return enumName.toString();
    }

    @NotNull
    private List<Named<PsiField>> getFields(Map<List<String>, List<Named<PsiField>>> prefixedFields, List<String> prefix) {
        List<Named<PsiField>> fieldsWithPrefix = prefixedFields.get(prefix);
        if (null == fieldsWithPrefix) {
            fieldsWithPrefix = new ArrayList<Named<PsiField>>();
            prefixedFields.put(prefix, fieldsWithPrefix);
        }
        return fieldsWithPrefix;
    }

}