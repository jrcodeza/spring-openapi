package com.github.jrcodeza.schema.v2.generator.domain.dummy;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

public class ValidationDummy {

	@DecimalMin("1.05")
	@DecimalMax("2.5")
	private BigDecimal decimalRange;

	@Min(3)
	@Max(7)
	private Integer minMax;

	@NotNull
	private String notNull;

	@Pattern(regexp = "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")
	private String regex;

	@Size(min = 2, max = 10)
	private String stringSize;

	@Size(max = 10)
	private String stringSizeOnlyMax;

	private String deprecated;

	@Deprecated
	private String javaDeprecated;

	private String writeOnly;

	private String readOnly;

	public BigDecimal getDecimalRange() {
		return decimalRange;
	}

	public void setDecimalRange(BigDecimal decimalRange) {
		this.decimalRange = decimalRange;
	}

	public Integer getMinMax() {
		return minMax;
	}

	public void setMinMax(Integer minMax) {
		this.minMax = minMax;
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

	public String getWriteOnly() {
		return writeOnly;
	}

	public void setWriteOnly(String writeOnly) {
		this.writeOnly = writeOnly;
	}

	public String getReadOnly() {
		return readOnly;
	}

	public void setReadOnly(String readOnly) {
		this.readOnly = readOnly;
	}
}
