package com.hiretrack.parse.parser;

import static com.hiretrack.parse.parser.ParserSupport.clean;
import static com.hiretrack.parse.parser.ParserSupport.meta;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.hiretrack.parse.ParseSource;
import com.hiretrack.parse.ParsedPosting;

/** 최후 수단: og:title / og:site_name / &lt;title&gt; 으로 제목·회사명만이라도 확보한다. */
@Component
@Order(200)
public class OpenGraphParser implements PostingParser {

    /** og:site_name 이 채용 플랫폼 이름이면 회사명으로 쓰지 않는다. */
    private static final Set<String> JOB_BOARDS = Set.of(
            "사람인", "잡코리아", "원티드", "wanted", "로켓펀치", "잡플래닛", "linkedin", "인크루트", "점핏", "programmers", "프로그래머스");

    /** "[회사명] 공고 제목" */
    private static final Pattern BRACKET_COMPANY = Pattern.compile("^\\[(.+?)]\\s*(.+)$");
    @Override
    public boolean supports(String host) {
        return true;
    }

    @Override
    public ParsedPosting parse(Document document) {
        String rawTitle = meta(document, "og:title");
        if (rawTitle == null) rawTitle = meta(document, "twitter:title");
        if (rawTitle == null) rawTitle = clean(document.title());
        if (rawTitle == null) return ParsedPosting.EMPTY;

        String siteName = meta(document, "og:site_name");
        String title = siteName == null ? rawTitle : stripSiteName(rawTitle, siteName);

        String company = null;
        Matcher bracket = BRACKET_COMPANY.matcher(title);
        if (bracket.matches()) {
            company = clean(bracket.group(1));
            title = clean(bracket.group(2));
        } else if (siteName != null && !isJobBoard(siteName)) {
            company = siteName;
        }
        return new ParsedPosting(company, title, null, null, null, null);
    }

    /** "제목 | 사람인", "제목 - 사람인" 처럼 끝에 붙은 사이트명을 뗀다. */
    private static String stripSiteName(String title, String siteName) {
        Matcher suffix = Pattern.compile("\\s*[|\\-–—:]\\s*" + Pattern.quote(siteName) + "\\s*$").matcher(title);
        String stripped = clean(suffix.replaceFirst(""));
        return stripped == null ? title : stripped;
    }

    private static boolean isJobBoard(String siteName) {
        String lower = siteName.toLowerCase();
        return JOB_BOARDS.stream().anyMatch(lower::contains);
    }

    @Override
    public ParseSource source() {
        return ParseSource.OPEN_GRAPH;
    }

    @Override
    public boolean siteSpecific() {
        return false;
    }
}
