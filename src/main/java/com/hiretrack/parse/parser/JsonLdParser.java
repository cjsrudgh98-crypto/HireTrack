package com.hiretrack.parse.parser;

import static com.hiretrack.parse.parser.ParserSupport.clean;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.hiretrack.common.config.TimeConfig;
import com.hiretrack.parse.ParseSource;
import com.hiretrack.parse.ParsedPosting;
import com.hiretrack.posting.domain.EmploymentType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** schema.org JobPosting 구조화 데이터(JSON-LD) 범용 파서. */
@Component
@Order(100)
public class JsonLdParser implements PostingParser {

    private static final int MAX_REQUIREMENTS = 3000;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JsonLdParser(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String host) {
        return true;
    }

    @Override
    public ParsedPosting parse(Document document) {
        return findJobPosting(document).map(this::toParsed).orElse(ParsedPosting.EMPTY);
    }

    private Optional<JsonNode> findJobPosting(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                Optional<JsonNode> found = findIn(objectMapper.readTree(script.data()));
                if (found.isPresent()) return found;
            } catch (JacksonException e) {
                // 깨진 JSON-LD 블록은 건너뛰고 다음 블록을 본다
            }
        }
        return Optional.empty();
    }

    /** 배열, @graph 안에 들어 있는 경우까지 JobPosting 노드를 찾는다. */
    private static Optional<JsonNode> findIn(JsonNode node) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<JsonNode> found = findIn(child);
                if (found.isPresent()) return found;
            }
            return Optional.empty();
        }
        if (!node.isObject()) return Optional.empty();
        if (hasType(node.path("@type"), "JobPosting")) return Optional.of(node);
        if (node.has("@graph")) return findIn(node.get("@graph"));
        return Optional.empty();
    }

    private static boolean hasType(JsonNode type, String wanted) {
        if (type.isString()) return type.asString().equals(wanted);
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (t.isString() && t.asString().equals(wanted)) return true;
            }
        }
        return false;
    }

    private ParsedPosting toParsed(JsonNode job) {
        JsonNode org = job.path("hiringOrganization");
        String company = org.isString() ? clean(org.asString()) : text(org.path("name"));
        return new ParsedPosting(
                company,
                text(job.path("title")),
                location(job),
                employmentType(job.path("employmentType")),
                deadline(text(job.path("validThrough"))),
                requirements(job));
    }

    private static String location(JsonNode job) {
        if ("TELECOMMUTE".equals(text(job.path("jobLocationType")))) return "원격";
        JsonNode location = job.path("jobLocation");
        if (location.isArray()) location = location.path(0);
        JsonNode address = location.path("address");
        if (address.isString()) return clean(address.asString());

        StringJoiner joiner = new StringJoiner(" ");
        for (String field : new String[]{"addressRegion", "addressLocality", "streetAddress"}) {
            String value = text(address.path(field));
            if (value != null && !joiner.toString().contains(value)) joiner.add(value);
        }
        return clean(joiner.toString());
    }

    private static EmploymentType employmentType(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) node.forEach(v -> values.add(v.asString()));
        else if (node.isString()) values.add(node.asString());

        for (String value : values) {
            switch (value.toUpperCase().replace('-', '_')) {
                case "FULL_TIME" -> { return EmploymentType.FULL_TIME; }
                case "CONTRACTOR" -> { return EmploymentType.FREELANCE; }
                case "PART_TIME", "TEMPORARY", "INTERN", "PER_DIEM", "VOLUNTEER", "OTHER" -> { return EmploymentType.OTHER; }
                default -> {
                    EmploymentType fromKorean = ParserSupport.employmentType(value);
                    if (fromKorean != null) return fromKorean;
                }
            }
        }
        return null;
    }

    private LocalDate deadline(String validThrough) {
        if (validThrough == null) return null;
        try {
            return OffsetDateTime.parse(validThrough).atZoneSameInstant(TimeConfig.ZONE).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // 오프셋 없는 형식으로 재시도
        }
        try {
            return LocalDateTime.parse(validThrough).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // 날짜만 있거나 비표준 형식
        }
        return ParserSupport.deadline(validThrough, LocalDate.now(clock));
    }

    /** qualifications → experience/education/skills → description 순으로 쓸 만한 값을 고른다. */
    private static String requirements(JsonNode job) {
        StringJoiner joiner = new StringJoiner("\n");
        for (String field : new String[]{"qualifications", "experienceRequirements", "educationRequirements", "skills"}) {
            String value = htmlText(job.path(field));
            if (value != null) joiner.add(value);
        }
        String result = joiner.length() > 0 ? joiner.toString() : htmlText(job.path("description"));
        return ParserSupport.truncate(result, MAX_REQUIREMENTS);
    }

    private static String htmlText(JsonNode node) {
        if (node.isObject()) node = node.path("description").isMissingNode() ? node.path("name") : node.path("description");
        String raw = text(node);
        return raw == null ? null : clean(Jsoup.parse(raw).text());
    }

    private static String text(JsonNode node) {
        return node.isString() || node.isNumber() ? clean(node.asString()) : null;
    }

    @Override
    public ParseSource source() {
        return ParseSource.JSON_LD;
    }

    @Override
    public boolean siteSpecific() {
        return false;
    }
}
