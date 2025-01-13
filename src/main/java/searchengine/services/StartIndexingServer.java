package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;


@Service
@Slf4j
@Transactional
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class StartIndexingServer implements IndexingService<IndexingResponse>{

    private final SitesList sitesList;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Transactional
    @Override
    public synchronized IndexingResponse startIndexing() {
        log.info("StartIndexing = {}", isIndexing);
        if (isIndexing.get()) {
            return createErrorResponse("Индексация уже запущена");
        }
        isIndexing.set(true);
        log.info("StartIndexing = {}", isIndexing);
        List<Site> sites = sitesList.getSites();
        for (Site site : sites) {
            executorService.submit(() -> indexSite(site));
        }
        return createSuccessResponse();
    }

    @Transactional
    @Override
    public synchronized IndexingResponse stopIndexing() {
        if (isIndexing.get()) {
            isIndexing.set(false);
            return createSuccessResponse();
        }
        return createErrorResponse("Индексация не запущена");
    }

    @Transactional
    @Override
    public synchronized IndexingResponse indexPage(String url) {
        log.info("Current isIndexing value: {}", isIndexing.get());

        if (pageRepository.findByPath(url) == null) {
            isIndexing.set(false);
            log.error("Page not found = {}", url);
            return createErrorResponse("Данная страница находится за пределами сайтов" +
                    " указанных в конфигурационном файле");
        }
        isIndexing.set(true);
        log.info("Indexing page = {}", url);
            PageModel pageModel = new PageModel();
            pageModel.setPath(url);
            pageRepository.save(pageModel);
            log.info("New page model created for URL: {}", url);

        PagesIndexing(pageModel.getSite(), url);
        log.info("indexPage = {}", url);
        return createSuccessResponse();
    }

    @Transactional
    public void indexSite(Site site) {
        try {
            if (!isIndexing.get()) {
                log.info("IndexSite isIndexing value: {}", isIndexing.get());
                return;
            }
            SiteModel siteModel = siteRepository.findByUrl(site.getUrl());
            if (siteModel != null) {
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    log.info("No active transaction found! = {}", siteModel);
                }
                pageRepository.deleteBySite(siteModel);
                siteRepository.delete(siteModel);
            }
            siteModel = new SiteModel();
            siteModel.setUrl(site.getUrl());
            siteModel.setName(site.getName());
            siteModel.setStatus(Status.INDEXING);
            siteRepository.save(siteModel);
            log.info("siteNews = {}", siteModel);

            PagesIndexing(siteModel, site.getUrl());

            if(isIndexing.get()) {
                siteModel.setStatus(Status.INDEXED);
            } else {
                siteModel.setStatus(Status.FAILED);
                siteModel.setLastError("Индексация остановлена пользователем");
            }
            siteRepository.save(siteModel);
        } catch (Exception e) {
            SiteModel siteModel = siteRepository.findByUrl(site.getUrl());
            log.info("siteException = {}", siteModel);
            if (siteModel != null) {
                siteModel.setStatus(Status.FAILED);
                siteModel.setLastError("Ошибка: " + e.getMessage());
                log.error("Error: = {}", e.getMessage());
                siteRepository.save(siteModel);
            } else {
                System.out.println("Сайт не найден по URL = " + site.getUrl());
                log.error("Site not found URL = {}", e.getMessage());
            }
        }
    }

    private void PagesIndexing(SiteModel siteModel, String url) {
        if(!isIndexing.get())
            return;
        ForkJoinPool pool = new ForkJoinPool();
        try {
            pool.invoke(new IndexingPages(isIndexing, url, pageRepository, siteRepository, siteModel));
        } finally {
            pool.shutdown();
        }
    }

    private IndexingResponse createErrorResponse(String error) {
        IndexingResponse indexingResponse = new IndexingResponse();
        indexingResponse.setResult(false);
        indexingResponse.setError(error);
        return indexingResponse;
    }

    private IndexingResponse createSuccessResponse() {
        IndexingResponse indexingResponse = new IndexingResponse();
        indexingResponse.setResult(true);
        return indexingResponse;
    }
}


