package org.spring.openapi.schema.generator.model.dummy;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.*;
import java.math.BigDecimal;

public class ValidationDummy {

    @DecimalMin("1.05")
    @DecimalMax("2.5")
    private BigDecimal decimalRange;

    @Email
    private String email;

    @Min(3)
    @Max(7)
    private Integer minMax;

    @Negative
    private Integer negative;

    @NegativeOrZero
    private Integer negativeOrZero;

    @NotEmpty
    @NotBlank
    private String notEmptyOrBlank;

    @NotNull
    private String notNull;

    @Pattern(regexp = "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")
    private String regex;

    @Positive
    private Integer positive;

    @PositiveOrZero
    private Integer positiveOrZero;

    @Size(min = 2, max = 10)
    private String stringSize;

    @Size(max = 10)
    private String stringSizeOnlyMax;

    @Schema(deprecated = true)
    private String deprecated;

    @Deprecated
    private String javaDeprecated;

    public BigDecimal getDecimalRange() {
        return decimalRange;
    }

    public void setDecimalRange(BigDecimal decimalRange) {
        this.decimalRange = decimalRange;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getMinMax() {
        return minMax;
    }

    public void setMinMax(Integer minMax) {
        this.minMax = minMax;
    }

    public Integer getNegative() {
        return negative;
    }

    public void setNegative(Integer negative) {
        this.negative = negative;
    }

    public Integer getNegativeOrZero() {
        return negativeOrZero;
    }

    public void setNegativeOrZero(Integer negativeOrZero) {
        this.negativeOrZero = negativeOrZero;
    }

    public String getNotEmptyOrBlank() {
        return notEmptyOrBlank;
    }

    public void setNotEmptyOrBlank(String notEmptyOrBlank) {
        this.notEmptyOrBlank = notEmptyOrBlank;
    }

    public String getNotNull() {
        return notNull;
    }

    public void setNotNull(String notNull) {
        this.notNull = notNull;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public Integer getPositive() {
        return positive;
    }

    public void setPositive(Integer positive) {
        this.positive = positive;
    }

    public Integer getPositiveOrZero() {
        return positiveOrZero;
    }

    public void setPositiveOrZero(Integer positiveOrZero) {
        this.positiveOrZero = positiveOrZero;
    }

    public String getStringSize() {
        return stringSize;
    }

    public void setStringSize(String stringSize) {
        this.stringSize = stringSize;
    }

    public String getStringSizeOnlyMax() {
        return stringSizeOnlyMax;
    }

    public void setStringSizeOnlyMax(String stringSizeOnlyMax) {
        this.stringSizeOnlyMax = stringSizeOnlyMax;
    }

    public String getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(String deprecated) {
        this.deprecated = deprecated;
    }

    public String getJavaDeprecated() {
        return javaDeprecated;
    }

    public void setJavaDeprecated(String javaDeprecated) {
        this.javaDeprecated = javaDeprecated;
    }
}
