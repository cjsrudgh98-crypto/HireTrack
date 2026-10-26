package com.hiretrack.parse.parser;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.hiretrack.posting.domain.EmploymentType;

/** 파서들이 공통으로 쓰는 텍스트·날짜·고용형태 해석 도우미. */
public final class ParserSupport {

    private static final Pattern FULL_DATE = Pattern.compile("(\\d{4})\\s*[./\\-년]\\s*(\\d{1,2})\\s*[./\\-월]\\s*(\\d{1,2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(?<![\\d.])(\\d{1,2})\\s*[./월]\\s*(\\d{1,2})(?![\\d.])");

    private ParserSupport() {
    }

    /** 앞에서부터 처음으로 값이 있는 셀렉터의 텍스트. */
    public static String firstText(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                String text = clean(element.text());
                if (text != null) return text;
            }
        }
        return null;
    }

    public static String meta(Document document, String property) {
        Element element = document.selectFirst("meta[property=" + property + "], meta[name=" + property + "]");
        return element == null ? null : clean(element.attr("content"));
    }

    /**
     * &lt;dt&gt;라벨&lt;/dt&gt;&lt;dd&gt;값&lt;/dd&gt; 또는 &lt;th&gt;라벨&lt;/th&gt;&lt;td&gt;값&lt;/td&gt; 구조에서 라벨에 해당하는 값을 찾는다.
     * 채용 사이트 요약 영역이 대부분 이 형태라 사이트 전용 파서에서 공통으로 쓴다.
     */
    public static String labeledValue(Element root, String... labels) {
        for (Element label : root.select("dt, th")) {
            String text = label.text().replaceAll("\\s", "");
            for (String wanted : labels) {
                if (text.equals(wanted)) {
                    Element value = label.nextElementSibling();
                    if (value != null && (value.is("dd") || value.is("td"))) {
                        String cleaned = clean(value.text());
                        if (cleaned != null) return cleaned;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 채용 사이트 메타 설명의 "경력:신입, 학력:대졸, 마감일:2026-07-27" 처럼 "항목 : 값" 이 쉼표로 이어진 형식에서
     * 원하는 항목의 값. 제목에 쉼표가 들어 있어도 항목 이름이 정확히 같은 조각만 보므로 섞이지 않는다.
     */
    public static String summaryValue(String summary, String key) {
        if (summary == null) return null;
        for (String part : summary.split(",")) {
            int colon = part.indexOf(':');
            if (colon > 0 && part.substring(0, colon).trim().equals(key)) return clean(part.substring(colon + 1));
        }
        return null;
    }

    /** 메타 설명에서 자격요건으로 쓸 항목들을 "항목: 값" 줄로 모은다 */
    public static String summaryRequirements(String summary, String... keys) {
        StringBuilder lines = new StringBuilder();
        for (String key : keys) {
            String value = summaryValue(summary, key);
            if (value != null) lines.append(lines.isEmpty() ? "" : "\n").append(key).append(": ").append(value);
        }
        return lines.isEmpty() ? null : lines.toString();
    }

    public static EmploymentType employmentType(String text) {
        if (text == null) return null;
        if (text.contains("정규직")) return EmploymentType.FULL_TIME;
        if (text.contains("프리랜서") || text.contains("위촉")) return EmploymentType.FREELANCE;
        if (text.matches(".*(계약직|인턴|파견|아르바이트|병역특례|교육생).*")) return EmploymentType.OTHER;
        return null;
    }

    /**
     * 문자열에서 마감일을 찾는다. 기간("2026.10.20 ~ 2026.11.05")이면 마지막 날짜를 쓴다.
     * 연도가 없는 "~11/05(목)" 형식은 today 기준으로 연도를 추정한다(일주일 이상 지난 날짜면 내년).
     */
    public static LocalDate deadline(String text, LocalDate today) {
        if (text == null || text.contains("상시") || text.contains("채용시")) return null;

        LocalDate found = null;
        Matcher full = FULL_DATE.matcher(text);
        while (full.find()) {
            LocalDate date = toDate(Integer.parseInt(full.group(1)), full.group(2), full.group(3));
            if (date != null) found = date;
        }
        if (found != null) return found;

        Matcher monthDay = MONTH_DAY.matcher(text);
        while (monthDay.find()) {
            LocalDate date = toDate(today.getYear(), monthDay.group(1), monthDay.group(2));
            if (date != null) found = date.isBefore(today.minusDays(7)) ? date.plusYears(1) : date;
        }
        return found;
    }

    public static String clean(String text) {
        if (text == null) return null;
        String trimmed = text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max - 1) + "…";
    }

    private static LocalDate toDate(int year, String month, String day) {
        try {
            return LocalDate.of(year, Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException e) {
            return null;
        }
    }
}
