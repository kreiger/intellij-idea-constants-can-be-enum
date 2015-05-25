package com.linuxgods.kreiger.intellij.idea.constants.enumclass;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Arrays.asList;

public class ConvertConstantsToEnumInspection extends BaseLocalInspectionTool {
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkClass(@NotNull final PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
        List<PsiField> fields = asList(psiClass.getFields());
        final Map<String, List<Named<PsiField>>> enumFields = new HashMap<String, List<Named<PsiField>>>();
        List<ProblemDescriptor> problemDescriptors = new ArrayList<ProblemDescriptor>();
        for (PsiField field : fields) {
            if (!ExpressionUtils.isConstant(field)) {
                continue;
            }
            String fieldName = field.getName();
            List<String> fieldNameComponents = asList(fieldName.split("_"));
            for (int i = fieldNameComponents.size() - 1; i > 0; i--) {
                List<String> prefix = fieldNameComponents.subList(0, i);
                List<String> suffix = fieldNameComponents.subList(i, fieldNameComponents.size());
                final String enumName = getEnumName(prefix);
                if (null != psiClass.findInnerClassByName(enumName, false)) {
                    continue;
                }
                String enumConstantName = getEnumConstantName(suffix);
                Named<PsiField> namedField = new Named<PsiField>(enumConstantName, field);
                List<Named<PsiField>> namedFields = getFields(enumFields, enumName);
                namedFields.add(namedField);
                problemDescriptors.add(manager.createProblemDescriptor(namedField.getValue(), (TextRange) null, "Create enum named '" + enumName + "'", ProblemHighlightType.WEAK_WARNING, isOnTheFly, new ConvertConstantsToEnumFix(psiClass, enumName, namedFields, namedField.getValue().getType())));
            }
        }
        return problemDescriptors.toArray(new ProblemDescriptor[problemDescriptors.size()]);
    }

    private String getEnumConstantName(List<String> suffixComponents) {
        StringBuilder enumConstantName = new StringBuilder();
        for (Iterator<String> iterator = suffixComponents.iterator(); iterator.hasNext(); ) {
            String suffixComponent = iterator.next();
            enumConstantName.append(suffixComponent);
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
    private List<Named<PsiField>> getFields(Map<String, List<Named<PsiField>>> prefixedFields, String prefix) {
        List<Named<PsiField>> fieldsWithPrefix = prefixedFields.get(prefix);
        if (null == fieldsWithPrefix) {
            fieldsWithPrefix = new ArrayList<Named<PsiField>>();
            prefixedFields.put(prefix, fieldsWithPrefix);
        }
        return fieldsWithPrefix;
    }

}