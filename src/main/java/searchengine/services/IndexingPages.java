package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;


@RequiredArgsConstructor
@Getter
@Setter
@Slf4j
public class IndexingPages extends RecursiveAction {
    private final AtomicBoolean stopIndexing;
    private final String url;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SiteModel siteModel;
    private String userAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:92.0) Gecko/20100101 Firefox/92.0";
    private String referrer = "https://www.google.com";

    @Override
    protected void compute() {
        if (!stopIndexing.get()) {
            return;
        }
        try {
            Document document = Jsoup.connect(url).userAgent(userAgent).referrer(referrer).get();
            int code = document.connection().response().statusCode();
            String content = document.html();
            sleep();
            PageModel pageModel = pageRepository.findByPath(url);
            if (pageModel == null) {
                createNewPageModel(code, content, url, siteModel);
                log.info("Создана новая страница: {}", url);

                siteModel.setStatusTime(LocalDateTime.now());
                log.info("Sites Model: {}", siteModel);
                updateSiteStatus(siteModel, Status.INDEXING, null);
            } else {
                log.error("Дубликат страницы: {}", content);
            }

            Elements elements = document.select("a[href]");
            List<IndexingPages> tasks = new ArrayList<>();
            for (Element element : elements) {
                String absUrl = element.absUrl("href");
                if (absUrl.startsWith(siteModel.getUrl()) && !pageRepository.existsByPath(absUrl)) {
                    tasks.add(new IndexingPages(stopIndexing, absUrl, pageRepository, siteRepository, siteModel));
                }
            }
            invokeAll(tasks);
        } catch (IOException e) {
            log.error("Error URL: {} {}", url, e.getMessage());
            updateSiteStatus(siteModel, Status.FAILED, e.getMessage());
        }
    }

    public synchronized void updateSiteStatus(SiteModel siteModel, Status status, String errorMessage) {
        siteModel.setStatus(status);
        siteModel.setStatusTime(LocalDateTime.now());
        siteModel.setLastError(errorMessage);
        siteRepository.save(siteModel);
    }

    public synchronized void createNewPageModel(int code, String content, String url, SiteModel siteModel) {
        PageModel pageModel = new PageModel();
        pageModel.setCode(code);
        pageModel.setContent(content);
        pageModel.setPath(url);
        pageModel.setSite(siteModel);
        pageRepository.save(pageModel);
    }

    private void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

