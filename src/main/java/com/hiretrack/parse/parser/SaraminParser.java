package com.hiretrack.parse.parser;

import static com.hiretrack.parse.parser.ParserSupport.clean;
import static com.hiretrack.parse.parser.ParserSupport.firstText;
import static com.hiretrack.parse.parser.ParserSupport.labeledValue;

import java.time.Clock;
import java.time.LocalDate;
import java.util.StringJoiner;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.hiretrack.parse.ParseSource;
import com.hiretrack.parse.ParsedPosting;

/**
 * 사람인 공고 상세(jobs/relay/view?rec_idx=...) 파서.
 * 상단 헤더(.jv_header)의 회사명·제목과 요약(.jv_summary)의 dt/dd, 접수기간(.info_period)을 읽는다.
 * 상세 본문은 별도 iframe 이라 자격요건은 요약의 경력·학력·우대 항목으로 구성한다.
 */
@Component
@Order(10)
public class SaraminParser implements PostingParser {

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
                requirements(root));
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
