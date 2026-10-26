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
 * 잡코리아 공고 상세(Recruit/GI_Read/...) 파서.
 * 제목 영역(.sumTit)의 회사명(.coName)·제목과 요약 표(.tbRow, .tbList)의 dt/dd, 마감일(.tbTime)을 읽는다.
 * 화면을 자바스크립트로 그리는 신규 페이지는 여기서 비어 있게 나오고, 뒤의 JSON-LD 파서가 채운다.
 */
@Component
@Order(20)
public class JobKoreaParser implements PostingParser {

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
                requirements(root));
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
