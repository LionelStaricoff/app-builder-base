package com.openlab.mx.share.experience.dto;

import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

public record ExperienceFormRequest(
        @NotBlank String name,
        @NotBlank String description,
        @Min(1) int durationMinutes,
        @Min(1) int minCapacity,
        @Min(1) int maxCapacity,
        @DecimalMin("0.0") BigDecimal price,
        @NotNull ExperienceCategory category,
        @NotNull RecurrenceType recurrenceType,
        @NotNull Double meetingLat,
        @NotNull Double meetingLng,
        @Size(max = 10) List<ScheduleEntryRequest> schedules,
        @Size(max = 5) List<MultipartFile> images
) {
}
