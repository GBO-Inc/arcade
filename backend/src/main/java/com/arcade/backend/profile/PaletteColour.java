package com.arcade.backend.profile;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The value is one of the {@link AvatarPalette} colours. Null is left to {@code @NotNull}. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PaletteColour.Validator.class)
public @interface PaletteColour {

  String message() default "must be one of the palette colours";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<PaletteColour, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return value == null || AvatarPalette.COLOURS.contains(value);
    }
  }
}
