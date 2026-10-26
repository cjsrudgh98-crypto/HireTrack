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
 * 사람인 공고 상세(jobs/relay/view?rec_idx=...) 파서.
 * <p>실제 페이지(2026-10 확인)는 본문을 자바스크립트로 나중에 불러오는 껍데기라, 서버가 한 번 요청해서는 본문이 없다.
 * 그래서 두 단계로 읽는다.
 * <ol>
 *   <li>브라우저에 그려진 HTML(확장 프로그램): 헤더(.jv_header)의 회사명·제목, 요약(.jv_summary)의 dt/dd, 접수기간(.info_period)</li>
 *   <li>서버가 받은 껍데기: og:title "[회사] 제목 - 사람인" 과 메타 설명 "회사, 제목, 경력:…, 학력:…, 마감일:YYYY-MM-DD" 로
 *       1에서 비어 있는 칸만 채운다 (근무지역·고용형태는 껍데기에 없다)</li>
 * </ol>
 */
@Component
@Order(10)
public class SaraminParser implements PostingParser {

    private static final Pattern OG_TITLE = Pattern.compile("^\\[(.+?)]\\s*(.+?)(?:\\s*-\\s*사람인)?$");

    private final Clock clock;

    public SaraminParser(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean supports(String host) {
        return host.equals("saramin.co.kr") || host.endsWith(".saramin.co.kr");
    }

    @Override
    public ParsedPosting parse(Document document) {
        Element root = document.selectFirst(".wrap_jview, .jview, #content");
        if (root == null) root = document.body();

        String company = firstText(document, ".jv_header .company", ".wrap_jv_header .company", ".company_name");
        String title = firstText(document, ".jv_header .tit_job", ".wrap_jv_header .tit_job", "h1.tit_job");

        String location = labeledValue(root, "근무지역", "근무지");
        if (location != null) location = clean(location.replace("지도", ""));

        Element periodBox = document.selectFirst(".info_period");
        String period = labeledValue(periodBox != null ? periodBox : root, "마감일", "접수마감");

        LocalDate today = LocalDate.now(clock);
        return new ParsedPosting(
                company,
                title,
                location,
                ParserSupport.employmentType(labeledValue(root, "근무형태", "고용형태")),
                ParserSupport.deadline(period, today),
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
        for (String label : new String[]{"경력", "학력", "우대사항", "필수사항"}) {
            String value = labeledValue(root, label);
            if (value != null) joiner.add(label + ": " + value);
        }
        return joiner.length() == 0 ? null : joiner.toString();
    }

    @Override
    public ParseSource source() {
        return ParseSource.SARAMIN;
    }
}
