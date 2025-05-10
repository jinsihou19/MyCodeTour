package org.vito.mycodetour.tours.service;

import com.intellij.openapi.ui.InputValidator;

import java.util.function.Function;

/**
 * 校验器
 *
 * @author vito
 * Created on 2025/1/1
 */
public class TourValidator implements InputValidator {

    private final Function<String, Boolean> validator;

    public TourValidator(Function<String, Boolean> validator) {
        this.validator = validator;
    }

    @Override
    public boolean checkInput(String inputString) {
        return validator.apply(inputString);
    }

    @Override
    public boolean canClose(String inputString) {
        return validator.apply(inputString);
    }
}
