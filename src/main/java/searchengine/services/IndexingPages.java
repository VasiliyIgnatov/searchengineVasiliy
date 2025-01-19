package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
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
import java.util.*;
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
    private final PageService pageService;
    private String userAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:92.0) Gecko/20100101 Firefox/92.0";
    private String referrer = "https://www.google.com";

    @Override
    protected void compute() {
        if (!stopIndexing.get()) {
           log.info("Индексация остановлена для url: {}", url);
            pageService.updateSiteStatus(siteModel, Status.FAILED, "Индексация остановлена пользователем");
            return;
        }
        try {
            Connection connection = Jsoup.connect(url).userAgent(userAgent).referrer(referrer);
            Connection.Response response = connection.execute();
            String contentType = response.contentType();
            log.info("Тип контента для url: {} {}", url, contentType);

            if(contentType == null || !contentType.startsWith("text/"))
            {
            log.warn("Неподдерживаемый тип контента для url: {} {}", url, contentType);
            return;
            }

            Document document = response.parse();
            int code = document.connection().response().statusCode();
            String content = document.html();
            sleep();

            if(!pageRepository.existsByPath(url)) {
                PageModel pageModel = createPageModel(code, content, url, siteModel);
                pageService.savePageModel(pageModel);
                log.info("Создана новая страница: {}", url);
            } else {
                log.warn("Дубликат страницы: {}", url);
            }

            Elements elements = document.select("a[href]");
            List<IndexingPages> tasks = new ArrayList<>();
            for (Element element : elements) {
                String absUrl = element.absUrl("href");
                if (absUrl.startsWith(siteModel.getUrl()) && !pageRepository.existsByPath(absUrl)) {

                    tasks.add(new IndexingPages(stopIndexing, absUrl, pageRepository, siteRepository, siteModel, pageService));
                   log.info("Добавлен новый Task для URL: {}", absUrl);
                }
            }
            invokeAll(tasks);
        } catch (IOException e) {
            log.error("Error URL: {} {}", url, e.getMessage());
            pageService.updateSiteStatus(siteModel, Status.FAILED, e.getMessage());
        }
    }

    public synchronized PageModel createPageModel(int code, String content, String url, SiteModel siteModel) {
        PageModel pageModel = new PageModel();
        pageModel.setCode(code);
        pageModel.setContent(content);
        pageModel.setPath(url);
        pageModel.setSite(siteModel);
        return pageModel;
    }


    private void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

