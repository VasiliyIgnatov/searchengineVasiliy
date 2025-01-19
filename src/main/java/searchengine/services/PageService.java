package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class PageService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    @Transactional
    public synchronized void updateSiteStatus(SiteModel siteModel, Status status, String errorMessage) {
        siteModel.setStatus(status);
        siteModel.setStatusTime(LocalDateTime.now());
        siteModel.setLastError(errorMessage);
        siteRepository.save(siteModel);
    }

    @Transactional
    public synchronized void savePageModel(PageModel pageModel) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                log.info("Не найдена активная транзакция! = {}", pageModel);
        }
        try {
             pageRepository.save(pageModel);
        } catch (Exception e) {
            log.error("Ошибка при сохранении PageModel: {}", e.getMessage());
            throw e;
        }
    }
}
