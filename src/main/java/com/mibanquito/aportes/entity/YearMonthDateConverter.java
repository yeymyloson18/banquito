package com.mibanquito.aportes.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Mapea YearMonth a DATE (siempre dia 1 del mes), ver V5__crear_aporte_mensual.sql.
 */
@Converter(autoApply = false)
public class YearMonthDateConverter implements AttributeConverter<YearMonth, LocalDate> {

    @Override
    public LocalDate convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : attribute.atDay(1);
    }

    @Override
    public YearMonth convertToEntityAttribute(LocalDate dbData) {
        return dbData == null ? null : YearMonth.from(dbData);
    }
}
