package com.mercatto.config;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test-only payload carrying a class-level (not field-level) Bean Validation constraint, used
 * exclusively by {@link ThrowingTestController} / {@link GlobalExceptionHandlerTest} to exercise
 * the {@code ObjectError} (global error, no {@code field}) branch of
 * {@link GlobalExceptionHandler#handleMethodArgumentNotValid}. A field-level annotation (like
 * {@code @NotBlank}) always yields a {@code FieldError} instead, so a dedicated
 * class-level {@link Constraint} is the simplest way to trigger a genuine global error.
 */
@ClassLevelValidatedPayload.AlwaysInvalid
public record ClassLevelValidatedPayload(String name) {

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = AlwaysInvalid.Validator.class)
    public @interface AlwaysInvalid {

        String message() default "class-level constraint violated";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        class Validator implements ConstraintValidator<AlwaysInvalid, ClassLevelValidatedPayload> {
            @Override
            public boolean isValid(ClassLevelValidatedPayload value, ConstraintValidatorContext context) {
                return false;
            }
        }
    }
}
