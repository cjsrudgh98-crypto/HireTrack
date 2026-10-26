package com.hiretrack.parse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.hiretrack.posting.domain.EmploymentType;

/** 파서가 뽑아낸 공고 정보. 못 찾은 항목은 null. */
public record ParsedPosting(
        String company,
        String title,
        String location,
        EmploymentType employmentType,
        LocalDate deadline,
        String requirements
) {

    public static final ParsedPosting EMPTY = new ParsedPosting(null, null, null, null, null, null);

    /** 회사명이나 직무명 중 하나라도 있으면 추출 성공으로 본다. */
    public boolean hasCore() {
        return company != null || title != null;
    }

    public boolean hasAllFields() {
        return missingFields().isEmpty();
    }

    /** 이 결과에서 비어 있는 항목만 other 의 값으로 채운다(우선순위가 높은 쪽이 this). */
    public ParsedPosting fillFrom(ParsedPosting other) {
        return new ParsedPosting(
                company != null ? company : other.company,
                title != null ? title : other.title,
                location != null ? location : other.location,
                employmentType != null ? employmentType : other.employmentType,
                deadline != null ? deadline : other.deadline,
                requirements != null ? requirements : other.requirements);
    }

    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        if (company == null) missing.add("company");
        if (title == null) missing.add("title");
        if (location == null) missing.add("location");
        if (employmentType == null) missing.add("employmentType");
        if (deadline == null) missing.add("deadline");
        if (requirements == null) missing.add("requirements");
        return missing;
    }
}
