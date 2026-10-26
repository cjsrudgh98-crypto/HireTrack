package com.hiretrack.parse.parser;

import static com.hiretrack.parse.parser.ParserSupport.clean;
import static com.hiretrack.parse.parser.ParserSupport.firstText;
import static com.hiretrack.parse.parser.ParserSupport.labeledValue;

import java.time.Clock;
import java.time.LocalDate;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.hiretrack.parse.ParseSource;
import com.hiretrack.parse.ParsedPosting;

/**
 * 잡코리아 공고 상세(Recruit/GI_Read/...) 파서.
 * <ol>
 *   <li>예전 화면: 제목 영역(.sumTit)의 회사명(.coName)·제목과 요약 표(.tbRow, .tbList)의 dt/dd, 마감일(.tbTime)</li>
 *   <li>현재 화면(2026-10 확인): 요약 표가 없다. og:title "회사 채용 - 제목 | 잡코리아" 와
 *       메타 설명 "경력 : …, 학력 : …, 마감일 : YYYY.MM.DD" 로 빈 칸을 채운다.
 *       근무지·고용형태는 페이지의 JSON-LD(JobPosting)에 있어서 뒤의 JSON-LD 파서가 채운다.</li>
 * </ol>
 */
@Component
@Order(20)
public class JobKoreaParser implements PostingParser {

    private static final Pattern OG_TITLE = Pattern.compile("^(.+?)\\s+채용\\s+-\\s+(.+?)(?:\\s*\\|\\s*잡코리아)?$");

    private final Clock clock;

    public JobKoreaParser(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean supports(String host) {
        return host.equals("jobkorea.co.kr") || host.endsWith(".jobkorea.co.kr");
    }

    @Override
    public ParsedPosting parse(Document document) {
        Element root = document.selectFirst(".artReadJobSum, .tbRow, #container");
        if (root == null) root = document.body();

        String company = firstText(document, ".sumTit .coName", ".coName");
        String title = null;
        Element heading = document.selectFirst(".sumTit .hd_3, .sumTit h3");
        if (heading != null) {
            // <h3><div class="header"><span class="coName">회사</span></div>제목</h3> — 회사명을 뺀 본문만 제목으로 쓴다
            title = clean(heading.ownText());
        }

        String deadlineText = firstText(document, ".tbTime .date", ".tbTime dd");
        if (deadlineText == null) deadlineText = labeledValue(document.body(), "마감일", "접수마감일");

        LocalDate today = LocalDate.now(clock);
        return new ParsedPosting(
                company,
                title,
                labeledValue(root, "근무지역", "지역", "근무지"),
                ParserSupport.employmentType(labeledValue(root, "고용형태", "근무형태")),
                ParserSupport.deadline(deadlineText, today),
                requirements(root))
                .fillFrom(fromMeta(document, today));
    }

    private static ParsedPosting fromMeta(Document document, LocalDate today) {
        String company = null;
        String title = null;
        String ogTitle = ParserSupport.meta(document, "og:title");
        if (ogTitle != null) {
            Matcher m = OG_TITLE.matcher(ogTitle);
            if (m.matches()) {
                company = clean(m.group(1));
                title = clean(m.group(2));
            }
        }
        String summary = ParserSupport.meta(document, "description");
        return new ParsedPosting(company, title, null, null,
                ParserSupport.deadline(ParserSupport.summaryValue(summary, "마감일"), today),
                ParserSupport.summaryRequirements(summary, "경력", "학력"));
    }

    private static String requirements(Element root) {
        StringJoiner joiner = new StringJoiner("\n");
        for (String label : new String[]{"경력", "학력", "우대", "스킬"}) {
            String value = labeledValue(root, label);
            if (value != null) joiner.add(label + ": " + value);
        }
        return joiner.length() == 0 ? null : joiner.toString();
    }

    @Override
    public ParseSource source() {
        return ParseSource.JOBKOREA;
    }
}
