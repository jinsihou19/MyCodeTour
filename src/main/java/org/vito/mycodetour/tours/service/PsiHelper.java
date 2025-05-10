package org.vito.mycodetour.tours.service;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author vito
 * @since 11.0
 * Created on 2025/5/11
 */
public class PsiHelper {

    /**
     * 构建方法引用
     *
     * @param method 方法
     * @return 方法引用
     */
    public static String methodWithParameter(PsiMethod method) {
        PsiParameter[] params = method.getParameterList().getParameters();
        String paramTypes = Arrays.stream(params)
                .map(p -> p.getType().getCanonicalText())
                .collect(Collectors.joining(", "));
        return String.format("%s(%s)", method.getName(), paramTypes);
    }

    /**
     * 构建类#方法引用
     *
     * @param method 方法
     * @return 方法引用
     */
    public static String buildMethodReference(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        String className = containingClass != null ?
                containingClass.getQualifiedName() : "<UnknownClass>";
        PsiParameter[] params = method.getParameterList().getParameters();
        String paramTypes = Arrays.stream(params)
                .map(p -> p.getType().getCanonicalText())
                .collect(Collectors.joining(", "));
        return String.format("%s#%s(%s)", className, method.getName(), paramTypes);
    }

    /**
     * 构建类#字段引用
     *
     * @param field 字段
     * @return 字段引用
     */
    public static String buildFieldReference(PsiField field) {
        PsiClass containingClass = field.getContainingClass();
        String className = containingClass != null ?
                containingClass.getQualifiedName() : "<UnknownClass>";
        return String.format("%s#%s", className, field.getName());
    }
}
